package com.vajrapulse.vortex;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A lightweight micro-batcher that groups requests and dispatches them to a backend.
 * Supports virtual threads, smart batching (size or time-based), and atomic commits.
 * 
 * @param <T> the type of request elements
 */
public class MicroBatcher<T> implements AutoCloseable {
    private final Backend<T> backend;
    private final BatcherConfig config;
    private final MeterRegistry meterRegistry;
    
    private final BlockingQueue<PendingRequest<T>> queue;
    private final ExecutorService executor;
    
    private volatile boolean closed = false;
    
    // Metrics
    private final Counter requestsSubmitted;
    private final Counter batchesDispatched;
    private final Counter requestsSucceeded;
    private final Counter requestsFailed;
    private final Counter requestsReplayed;
    private final Timer batchDispatchLatency;
    private final Timer requestWaitLatency;
    private final Timer queueWaitTime; // Additional metric for queue wait time with percentiles
    private final AtomicInteger queueDepth = new AtomicInteger(0);
    
    // Per-item metrics (only created if perItemMetrics is enabled)
    private final Timer itemSubmitLatency;
    private final Timer itemWaitTime;
    private final io.micrometer.core.instrument.DistributionSummary itemBatchSize;
    
    // Batch size distribution metrics
    private final io.micrometer.core.instrument.DistributionSummary batchSizeHistogram;
    
    /**
     * Creates a new MicroBatcher with a default SimpleMeterRegistry.
     * 
     * @param backend the backend implementation
     * @param config the batcher configuration
     */
    public MicroBatcher(Backend<T> backend, BatcherConfig config) {
        this(backend, config, new SimpleMeterRegistry());
    }
    
    /**
     * Creates a new MicroBatcher with the specified MeterRegistry.
     * 
     * @param backend the backend implementation
     * @param config the batcher configuration
     * @param meterRegistry the meter registry for metrics (must not be null)
     * @throws IllegalArgumentException if backend or config is null
     */
    public MicroBatcher(Backend<T> backend, BatcherConfig config, MeterRegistry meterRegistry) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (meterRegistry == null) {
            throw new IllegalArgumentException("MeterRegistry cannot be null");
        }
        
        this.backend = backend;
        this.config = config;
        this.meterRegistry = meterRegistry;
        
        // Use virtual threads for executor
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Queue size should be reasonable - use 2x batch size as default
        this.queue = new LinkedBlockingQueue<>(config.getBatchSize() * 2);
        
        // Register metrics
        this.requestsSubmitted = Counter.builder("vortex.requests.submitted")
            .description("Total number of requests submitted")
            .register(meterRegistry);
        
        this.batchesDispatched = Counter.builder("vortex.batches.dispatched")
            .description("Total number of batches dispatched")
            .register(meterRegistry);
        
        this.requestsSucceeded = Counter.builder("vortex.requests.succeeded")
            .description("Total number of requests that succeeded")
            .register(meterRegistry);
        
        this.requestsFailed = Counter.builder("vortex.requests.failed")
            .description("Total number of requests that failed")
            .register(meterRegistry);
        
        this.requestsReplayed = Counter.builder("vortex.requests.replayed")
            .description("Total number of successful requests that were replayed")
            .register(meterRegistry);
        
        this.batchDispatchLatency = Timer.builder("vortex.batch.dispatch.latency")
            .description("Time taken to dispatch a batch")
            .register(meterRegistry);
        
        this.requestWaitLatency = Timer.builder("vortex.request.wait.latency")
            .description("Time a request waits before being batched")
            .register(meterRegistry);
        
        // Queue wait time metric with percentiles
        this.queueWaitTime = Timer.builder("vortex.queue.wait.time")
            .description("Distribution of queue wait times")
            .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
            .register(meterRegistry);
        
        // Batch size distribution metrics
        this.batchSizeHistogram = io.micrometer.core.instrument.DistributionSummary.builder("vortex.batch.size")
            .description("Distribution of batch sizes")
            .register(meterRegistry);
        
        // Per-item metrics (only created if enabled)
        Timer itemSubmitLatencyTemp = null;
        Timer itemWaitTimeTemp = null;
        io.micrometer.core.instrument.DistributionSummary itemBatchSizeTemp = null;
        if (config.isPerItemMetrics()) {
            itemSubmitLatencyTemp = Timer.builder("vortex.item.submit.latency")
                .description("Time from submit to batch completion (per item)")
                .register(meterRegistry);
            itemWaitTimeTemp = Timer.builder("vortex.item.wait.time")
                .description("Time item waits in queue before batching")
                .register(meterRegistry);
            itemBatchSizeTemp = io.micrometer.core.instrument.DistributionSummary.builder("vortex.item.batch.size")
                .description("Size of batch when item was processed")
                .register(meterRegistry);
        }
        this.itemSubmitLatency = itemSubmitLatencyTemp;
        this.itemWaitTime = itemWaitTimeTemp;
        this.itemBatchSize = itemBatchSizeTemp;
        
        Gauge.builder("vortex.queue.depth", queueDepth, AtomicInteger::get)
            .description("Current depth of the request queue")
            .register(meterRegistry);
        
        // Start the batch processor
        startBatchProcessor();
    }
    
    /**
     * Submits a request to be batched and dispatched.
     * 
     * @param data the request data
     * @return a CompletableFuture that completes with the batch result
     */
    public CompletableFuture<BatchResult<T>> submit(T data) {
        if (closed) {
            throw new IllegalStateException("MicroBatcher is closed");
        }
        
        requestsSubmitted.increment();
        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(data, future);
        
        try {
            if (!queue.offer(request, 100, TimeUnit.MILLISECONDS)) {
                future.completeExceptionally(new RejectedExecutionException("Queue is full"));
                return future;
            }
            queueDepth.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Submits an item and registers a callback for when its batch completes.
     * The callback receives the item and its result (success or failure).
     * 
     * @param item the item to submit
     * @param callback callback to execute when batch completes, receives (item, ItemResult)
     * @return CompletableFuture that completes when the callback finishes
     */
    public CompletableFuture<Void> submitWithCallback(T item, java.util.function.BiConsumer<T, ItemResult<T>> callback) {
        CompletableFuture<BatchResult<T>> future = submit(item);
        return future.thenAccept(result -> {
            ItemResult<T> itemResult = result.findItemResult(item)
                .orElseThrow(() -> new IllegalStateException("Item result not found for submitted item"));
            callback.accept(item, itemResult);
        });
    }
    
    private void startBatchProcessor() {
        // Process batches - handles both size and time-based triggers
        executor.submit(() -> {
            while (!closed || !queue.isEmpty()) {
                try {
                    processBatch();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Log error but continue processing
                    System.err.println("Error in batch processor: " + e.getMessage());
                }
            }
        });
    }
    
    private void processBatch() throws InterruptedException {
        List<PendingRequest<T>> batch = new ArrayList<>();
        
        // Wait for first item with timeout based on linger time
        PendingRequest<T> first = queue.poll(config.getLingerTime().toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return;
        }
        
        batch.add(first);
        queueDepth.decrementAndGet();
        
        // Collect up to batchSize items, respecting linger time
        // Whichever comes first: batch size reached or linger time elapsed
        long deadline = System.nanoTime() + config.getLingerTime().toNanos();
        while (batch.size() < config.getBatchSize()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break; // Linger time elapsed
            }
            
            PendingRequest<T> next = queue.poll(
                Math.max(1, Duration.ofNanos(remaining).toMillis()),
                TimeUnit.MILLISECONDS
            );
            if (next == null) {
                break; // Timeout - trigger batch
            }
            batch.add(next);
            queueDepth.decrementAndGet();
        }
        
        if (!batch.isEmpty()) {
            dispatchBatch(batch);
        }
    }
    
    private void dispatchBatch(List<PendingRequest<T>> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        batchesDispatched.increment();
        
        // Record batch size distribution
        batchSizeHistogram.record(batch.size());
        
        @SuppressWarnings("null") // meterRegistry is checked for null in constructor
        Timer.Sample sample = Timer.start(meterRegistry);
        
        // Record per-item batch size if enabled
        if (config.isPerItemMetrics() && itemBatchSize != null) {
            for (PendingRequest<T> req : batch) {
                itemBatchSize.record(batch.size());
            }
        }
        
        List<T> dataList = batch.stream()
            .map(PendingRequest::getData)
            .toList();
        
        // Execute backend dispatch on a virtual thread
        // Virtual threads are perfect for I/O-bound operations like HTTP calls, DB queries
        executor.submit(() -> {
            try {
                BatchResult<T> result = backend.dispatch(dataList);
                @SuppressWarnings("null") // batchDispatchLatency is initialized in constructor
                Timer timer = batchDispatchLatency;
                sample.stop(timer);
                processBatchResults(batch, result);
            } catch (Exception e) {
                @SuppressWarnings("null") // batchDispatchLatency is initialized in constructor
                Timer timer = batchDispatchLatency;
                sample.stop(timer);
                handleBatchFailure(batch, e);
            }
        });
    }
    
    private void processBatchResults(List<PendingRequest<T>> batch, BatchResult<T> result) {
        if (config.isAtomicCommit() && !result.isAllSuccess()) {
            // In atomic mode, if any fails, all fail
            @SuppressWarnings("null") // requestWaitLatency and queueWaitTime are initialized in constructor
            Timer waitLatency = requestWaitLatency;
            Timer queueWait = queueWaitTime;
            for (PendingRequest<T> req : batch) {
                long waitTime = System.nanoTime() - req.getTimestamp();
                waitLatency.record(waitTime, TimeUnit.NANOSECONDS);
                queueWait.record(waitTime, TimeUnit.NANOSECONDS);
                
                requestsFailed.increment();
                req.getFuture().complete(new BatchResult<>(
                    List.of(),
                    List.of(new FailureEvent<>(req.getData(), 
                        new RuntimeException("Batch failed due to atomic commit requirement")))
                ));
            }
        } else {
            // Map results back to individual requests
            // Assumes backend returns results in same order as input
            List<SuccessEvent<T>> successes = result.getSuccesses();
            List<FailureEvent<T>> failures = result.getFailures();
            
            // Check if replay is needed: backend decides first, config is fallback
            // Only replay when we have both successes and failures
            boolean shouldReplay = false;
            if (!successes.isEmpty() && !failures.isEmpty()) {
                // Backend can decide if replay is needed based on the result
                // If backend returns true, replay. If false, check config as fallback.
                // This allows backends to opt-in explicitly, while config provides default behavior
                boolean backendWantsReplay = backend.shouldReplaySuccesses(result);
                if (backendWantsReplay) {
                    shouldReplay = true;
                } else {
                    // Backend said no or used default - use config as fallback
                    shouldReplay = config.isAutoReplaySuccesses();
                }
            }
            
            // Replay successful items if needed
            if (shouldReplay) {
                replaySuccessfulItems(successes);
            }
            
            int successIdx = 0;
            int failureIdx = 0;
            
            @SuppressWarnings("null") // requestWaitLatency and queueWaitTime are initialized in constructor
            Timer waitLatency = requestWaitLatency;
            Timer queueWait = queueWaitTime;
            long batchCompletionTime = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                long waitTime = batchCompletionTime - req.getTimestamp();
                waitLatency.record(waitTime, TimeUnit.NANOSECONDS);
                queueWait.record(waitTime, TimeUnit.NANOSECONDS);
                
                // Record per-item metrics if enabled
                if (config.isPerItemMetrics()) {
                    if (itemWaitTime != null) {
                        itemWaitTime.record(waitTime, TimeUnit.NANOSECONDS);
                    }
                    if (itemSubmitLatency != null) {
                        itemSubmitLatency.record(waitTime, TimeUnit.NANOSECONDS);
                    }
                }
                
                // Check if this request succeeded or failed
                // Backend should maintain order, but we handle mismatches gracefully
                boolean matched = false;
                if (successIdx < successes.size()) {
                    T successData = successes.get(successIdx).getData();
                    if (successData != null && successData.equals(req.getData())) {
                        requestsSucceeded.increment();
                        req.getFuture().complete(new BatchResult<>(
                            List.of(successes.get(successIdx)),
                            List.of()
                        ));
                        successIdx++;
                        matched = true;
                    }
                }
                if (!matched && failureIdx < failures.size()) {
                    T failureData = failures.get(failureIdx).getData();
                    if (failureData != null && failureData.equals(req.getData())) {
                        requestsFailed.increment();
                        req.getFuture().complete(new BatchResult<>(
                            List.of(),
                            List.of(failures.get(failureIdx))
                        ));
                        failureIdx++;
                        matched = true;
                    }
                }
                if (!matched) {
                    // Fallback: if order doesn't match, distribute proportionally
                    if (successIdx < successes.size()) {
                        requestsSucceeded.increment();
                        req.getFuture().complete(new BatchResult<>(
                            List.of(new SuccessEvent<>(req.getData())),
                            List.of()
                        ));
                        successIdx++;
                    } else {
                        requestsFailed.increment();
                        Throwable failureError = failureIdx < failures.size() ?
                            failures.get(failureIdx).getError() :
                            new RuntimeException("Request failed in batch");
                        req.getFuture().complete(new BatchResult<>(
                            List.of(),
                            List.of(new FailureEvent<>(req.getData(), failureError))
                        ));
                        if (failureIdx < failures.size()) {
                            failureIdx++;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Replays successful items by re-submitting them to the batcher.
     * This allows successful items to be processed again before returning failures.
     */
    private void replaySuccessfulItems(List<SuccessEvent<T>> successes) {
        for (SuccessEvent<T> success : successes) {
            try {
                // Re-submit the successful item for another attempt
                submit(success.getData());
                requestsReplayed.increment();
            } catch (IllegalStateException e) {
                // Batcher is closed, skip replay
                break;
            } catch (Exception e) {
                // Log but continue with other replays
                System.err.println("Error replaying successful item: " + e.getMessage());
            }
        }
    }
    
    private void handleBatchFailure(List<PendingRequest<T>> batch, Throwable error) {
        @SuppressWarnings("null") // requestWaitLatency and queueWaitTime are initialized in constructor
        Timer waitLatency = requestWaitLatency;
        Timer queueWait = queueWaitTime;
        for (PendingRequest<T> req : batch) {
            long waitTime = System.nanoTime() - req.getTimestamp();
            waitLatency.record(waitTime, TimeUnit.NANOSECONDS);
            queueWait.record(waitTime, TimeUnit.NANOSECONDS);
            
            requestsFailed.increment();
            req.getFuture().complete(new BatchResult<>(
                List.of(),
                List.of(new FailureEvent<>(req.getData(), error))
            ));
        }
    }
    
    @Override
    public void close() {
        closed = true;
        
        // Wait for batch processor to finish processing queue (with timeout)
        long deadline = System.currentTimeMillis() + 2000; // 2 second timeout
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown executor gracefully to allow in-flight batches to complete
        executor.shutdown();
        
        try {
            // Wait for in-flight batches to complete
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
        
        // Process any remaining items synchronously after executor is done
        List<PendingRequest<T>> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            List<T> dataList = remaining.stream()
                .map(PendingRequest::getData)
                .toList();
            
            try {
                BatchResult<T> result = backend.dispatch(dataList);
                processBatchResults(remaining, result);
            } catch (Exception e) {
                handleBatchFailure(remaining, e);
            }
        }
    }
    
    /**
     * Gets the MeterRegistry used for metrics.
     * 
     * @return the meter registry
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}

