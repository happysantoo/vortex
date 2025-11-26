package com.vajrapulse.vortex;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(MicroBatcher.class);
    
    // Configuration constants
    private static final int QUEUE_OFFER_TIMEOUT_MS = 100;
    private static final int CLOSE_QUEUE_WAIT_TIMEOUT_MS = 2000;
    private static final int CLOSE_POLL_INTERVAL_MS = 10;
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 5;
    
    private final Backend<T> backend;
    private final BatcherConfig config;
    private final MeterRegistry meterRegistry;
    
    private final BlockingQueue<PendingRequest<T>> queue;
    private final ExecutorService executor;
    
    private volatile boolean closed = false;
    
    // Dynamic configuration (mutable, thread-safe)
    private volatile int currentBatchSize;
    private volatile Duration currentLingerTime;
    
    // Helper classes
    private final AtomicInteger queueDepth = new AtomicInteger(0);
    private final MetricsManager metrics;
    private final RetryManager<T> retryManager;
    private final ResultProcessor<T> resultProcessor;
    
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
        
        // Initialize dynamic config from static config
        this.currentBatchSize = config.getBatchSize();
        this.currentLingerTime = config.getLingerTime();
        
        // Initialize helper classes
        // NOTE: RetryManager and ResultProcessor receive this::submit as a parameter.
        // This creates a circular dependency, but it's safe because:
        // 1. The lambda captures 'this' but doesn't execute until after construction completes
        // 2. submit() checks 'closed' flag before processing, preventing issues during shutdown
        // 3. All required fields (queue, executor, metrics) are initialized before this point
        this.metrics = new MetricsManager(meterRegistry, config, queueDepth);
        this.retryManager = new RetryManager<>(config, executor, this::submit, () -> closed);
        this.resultProcessor = new ResultProcessor<>(config, backend, metrics, retryManager, this::submit);
        
        // Start the batch processor
        startBatchProcessor();
    }
    
    /**
     * Submits a request to be batched and dispatched.
     * 
     * <p>This method is thread-safe and can be called from multiple threads concurrently.
     * The request will be added to the batching queue and processed according to the
     * configured batch size and linger time settings.
     * 
     * <p>If the batcher is closed, this method will throw {@link IllegalStateException}.
     * If the queue is full, the returned future will complete exceptionally with
     * {@link RejectedExecutionException}.
     * 
     * @param data the request data
     * @return a CompletableFuture that completes with the batch result
     * @throws IllegalStateException if the batcher is closed
     */
    public CompletableFuture<BatchResult<T>> submit(T data) {
        if (closed) {
            throw new IllegalStateException("MicroBatcher is closed");
        }
        
        metrics.recordRequestSubmitted();
        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(data, future);
        
        try {
            if (!queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
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
     * <p>This method is thread-safe and provides a convenient way to handle results
     * without manually extracting them from the BatchResult.
     * 
     * <p>If the callback throws an exception, the returned future will complete
     * exceptionally with that exception.
     * 
     * @param item the item to submit
     * @param callback callback to execute when batch completes, receives (item, ItemResult)
     * @return CompletableFuture that completes when the callback finishes
     * @throws IllegalStateException if the batcher is closed
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
        executor.submit(() -> {
            while (!closed || !queue.isEmpty()) {
                try {
                    processBatch();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in batch processor", e);
                }
            }
        });
    }
    
    private void processBatch() throws InterruptedException {
        List<PendingRequest<T>> batch = new ArrayList<>();
        
        Duration lingerTime = currentLingerTime;
        int batchSize = currentBatchSize;
        
        // Wait for first item with timeout based on linger time
        PendingRequest<T> first = queue.poll(lingerTime.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return;
        }
        
        batch.add(first);
        queueDepth.decrementAndGet();
        
        if (config.isDebugMode()) {
            logger.debug("Starting batch formation, first item: {}", first.getData());
        }
        
        // Collect up to batchSize items, respecting linger time
        long deadline = System.nanoTime() + lingerTime.toNanos();
        while (batch.size() < batchSize) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                if (config.isDebugMode()) {
                    logger.debug("Linger time elapsed, batch size: {}", batch.size());
                }
                break;
            }
            
            PendingRequest<T> next = queue.poll(
                Math.max(1, Duration.ofNanos(remaining).toMillis()),
                TimeUnit.MILLISECONDS
            );
            if (next == null) {
                if (config.isDebugMode()) {
                    logger.debug("Timeout waiting for next item, batch size: {}", batch.size());
                }
                break;
            }
            batch.add(next);
            queueDepth.decrementAndGet();
            if (config.isDebugMode()) {
                logger.debug("Added item to batch, current size: {}, queue depth: {}", 
                    batch.size(), queueDepth.get());
            }
        }
        
        if (!batch.isEmpty()) {
            if (config.isDebugMode()) {
                logger.debug("Dispatching batch of size: {}", batch.size());
            }
            dispatchBatch(batch);
        }
    }
    
    private void dispatchBatch(List<PendingRequest<T>> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        metrics.recordBatchDispatched();
        
        if (config.isDebugMode()) {
            long waitTime = batch.stream()
                .mapToLong(req -> System.nanoTime() - req.getTimestamp())
                .sum() / batch.size();
            logger.debug("Dispatching batch: size={}, avgWaitTimeNs={}", batch.size(), waitTime);
        }
        
        metrics.recordBatchSize(batch.size());
        
        Timer.Sample sample = metrics.startBatchDispatchTimer();
        
        // Record per-item batch size if enabled
        if (config.isPerItemMetrics()) {
            metrics.recordItemBatchSize(batch.size());
        }
        
        List<T> dataList = batch.stream()
            .map(PendingRequest::getData)
            .toList();
        
        // Execute backend dispatch on a virtual thread
        executor.submit(() -> {
            try {
                if (config.isDebugMode()) {
                    logger.debug("Calling backend.dispatch() for batch of size: {}", dataList.size());
                }
                BatchResult<T> result = backend.dispatch(dataList);
                metrics.recordBatchDispatchLatency(sample);
                if (config.isDebugMode()) {
                    logger.debug("Backend dispatch completed: successes={}, failures={}", 
                        result.getSuccesses().size(), result.getFailures().size());
                }
                resultProcessor.processResults(batch, result);
            } catch (Exception e) {
                metrics.recordBatchDispatchLatency(sample);
                if (config.isDebugMode()) {
                    logger.debug("Backend dispatch failed", e);
                }
                resultProcessor.processFailure(batch, e);
            }
        });
    }
    
    /**
     * Closes the batcher, gracefully shutting down processing.
     * 
     * <p>This method:
     * <ul>
     *   <li>Stops accepting new submissions (subsequent calls to submit() will throw IllegalStateException)</li>
     *   <li>Waits for the batch processor to finish processing items already in the queue (up to 2 seconds)</li>
     *   <li>Shuts down the executor gracefully, allowing in-flight batches to complete (up to 5 seconds)</li>
     *   <li>Processes any remaining items synchronously after executor shutdown</li>
     * </ul>
     * 
     * <p>Note: Items submitted after close() is called will be rejected. Items already in the queue
     * will be processed, but there's no guarantee all items will be processed if shutdown times out.
     * 
     * <p>This method is idempotent - calling it multiple times has no additional effect.
     * 
     * <p>Thread safety: This method is thread-safe and can be called from any thread.
     */
    @Override
    public void close() {
        closed = true;
        
        retryManager.clearAll();
        
        // Wait for batch processor to finish processing queue (with timeout)
        // NOTE: This is a best-effort wait. Items submitted after close() is called
        // will be rejected, but items already in the queue will be processed.
        long deadline = System.currentTimeMillis() + CLOSE_QUEUE_WAIT_TIMEOUT_MS;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(CLOSE_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown executor gracefully to allow in-flight batches to complete
        executor.shutdown();
        
        try {
            if (!executor.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
                resultProcessor.processResults(remaining, result);
            } catch (Exception e) {
                resultProcessor.processFailure(remaining, e);
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
    
    /**
     * Updates the batch size dynamically at runtime.
     * The new batch size will be used for future batches (not the current batch being formed).
     * 
     * <p>Thread safety: This method is thread-safe. Updates to batch size are immediately visible
     * to the batch processor, but may not affect a batch currently being formed.
     * 
     * @param newBatchSize the new batch size (must be positive)
     * @throws IllegalArgumentException if newBatchSize is not positive
     * @throws IllegalStateException if the batcher is closed
     */
    public void updateBatchSize(int newBatchSize) {
        if (closed) {
            throw new IllegalStateException("MicroBatcher is closed");
        }
        if (newBatchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        
        if (config.isDebugMode()) {
            logger.debug("Updating batch size from {} to {}", currentBatchSize, newBatchSize);
        }
        
        this.currentBatchSize = newBatchSize;
    }
    
    /**
     * Updates the linger time dynamically at runtime.
     * The new linger time will be used for future batches (not the current batch being formed).
     * 
     * <p>Thread safety: This method is thread-safe. Updates to linger time are immediately visible
     * to the batch processor, but may not affect a batch currently being formed.
     * 
     * @param newLingerTime the new linger time (must be non-negative)
     * @throws IllegalArgumentException if newLingerTime is null or negative
     * @throws IllegalStateException if the batcher is closed
     */
    public void updateLingerTime(Duration newLingerTime) {
        if (closed) {
            throw new IllegalStateException("MicroBatcher is closed");
        }
        if (newLingerTime == null || newLingerTime.isNegative()) {
            throw new IllegalArgumentException("Linger time must be non-negative");
        }
        
        if (config.isDebugMode()) {
            logger.debug("Updating linger time from {} to {}", currentLingerTime, newLingerTime);
        }
        
        this.currentLingerTime = newLingerTime;
    }
    
    /**
     * Gets the current batch size (may differ from initial config if updated dynamically).
     * 
     * @return the current batch size
     */
    public int getCurrentBatchSize() {
        return currentBatchSize;
    }
    
    /**
     * Gets the current linger time (may differ from initial config if updated dynamically).
     * 
     * @return the current linger time
     */
    public Duration getCurrentLingerTime() {
        return currentLingerTime;
    }
    
    boolean isClosed() {
        return closed;
    }
}
