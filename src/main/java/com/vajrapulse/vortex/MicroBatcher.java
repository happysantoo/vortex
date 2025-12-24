package com.vajrapulse.vortex;

import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.results.ItemResult;
import com.vajrapulse.vortex.internal.BatchDispatcher;
import com.vajrapulse.vortex.internal.BatchFormationStrategy;
import com.vajrapulse.vortex.internal.DefaultBatcherDiagnostics;
import com.vajrapulse.vortex.internal.EnqueueResult;
import com.vajrapulse.vortex.internal.PendingRequest;
import com.vajrapulse.vortex.internal.RetryManager;
import com.vajrapulse.vortex.internal.ResultProcessor;
import com.vajrapulse.vortex.internal.ShutdownManager;
import com.vajrapulse.vortex.internal.SubmissionContext;
import com.vajrapulse.vortex.internal.SubmissionHandler;
import com.vajrapulse.vortex.internal.TracingHelper;
import com.vajrapulse.vortex.metrics.MetricsManager;
import com.vajrapulse.vortex.metrics.MetricsProvider;
import com.vajrapulse.vortex.health.BatcherDiagnostics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    
    private final Backend<T> backend;
    private final BatcherConfig config;
    private final MeterRegistry meterRegistry;
    
    private final BlockingQueue<PendingRequest<T>> queue;
    private final ExecutorService executor;
    
    // Concurrent dispatch limiting (optional)
    private final Semaphore dispatchSemaphore;
    private final AtomicInteger activeBatchCount;
    
    private volatile boolean closed = false;
    
    // Cached configuration for performance
    private final boolean debugMode;
    private final BatchTracingHook tracingHook;
    
    // Helper classes
    private final MetricsManager metrics;
    private final RetryManager<T> retryManager;
    private final ResultProcessor<T> resultProcessor;
    private final BatchFormationStrategy<T> batchFormationStrategy;
    private final BatchDispatcher<T> batchDispatcher;
    private final SubmissionHandler<T> submissionHandler;
    private final ShutdownManager<T> shutdownManager;
    
    /**
     * Creates a new MicroBatcher with a default SimpleMeterRegistry.
     * 
     * @param backend the backend implementation
     * @param config the batcher configuration
     * @throws IllegalArgumentException if backend or config is null
     */
    public MicroBatcher(Backend<T> backend, BatcherConfig config) {
        this(backend, config, new SimpleMeterRegistry());
    }
    
    /**
     * Creates a new MicroBatcher with the specified MeterRegistry.
     * 
     * <p>Example:
     * <pre>{@code
     * BatcherConfig config = BatcherConfig.builder()
     *     .batchSize(10)
     *     .lingerTime(Duration.ofMillis(100))
     *     .build();
     * 
     * MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, meterRegistry);
     * }</pre>
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
        
        // Initialize concurrent dispatch limiting
        int maxConcurrentBatches = config.getMaxConcurrentBatches();
        if (maxConcurrentBatches > 0) {
            this.dispatchSemaphore = new Semaphore(maxConcurrentBatches);
            this.activeBatchCount = new AtomicInteger(0);
        } else {
            this.dispatchSemaphore = null;  // No limit
            this.activeBatchCount = null;
        }
        
        // Use virtual threads for executor
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Queue size is configurable via BatcherConfig.maxQueueSize (defaults to 2x batch size)
        this.queue = new LinkedBlockingQueue<>(config.getMaxQueueSize());
        
        // Cached configuration for performance / observability
        this.debugMode = config.isDebugMode();
        this.tracingHook = config.getTracingHook();
        
        // Initialize helper classes
        // NOTE: RetryManager and ResultProcessor receive this::submit as a parameter.
        // This creates a circular dependency, but it's safe because:
        // 1. The lambda captures 'this' but doesn't execute until after construction completes
        // 2. submit() checks 'closed' flag before processing, preventing issues during shutdown
        // 3. All required fields (queue, executor, metrics) are initialized before this point
        this.metrics = new MetricsManager(meterRegistry, config, queue);
        
        // Register gauge for active concurrent batches if limiting is enabled
        if (activeBatchCount != null) {
            Gauge.builder("vortex.dispatch.active.batches", activeBatchCount, AtomicInteger::get)
                .description("Current number of batches being dispatched concurrently")
                .register(meterRegistry);
        }
        
        // RetryManager and ResultProcessor need CompletableFuture<BatchResult<T>> for retries/replays
        // Use internal method that provides this interface
        this.retryManager = new RetryManager<>(config, executor, this::submitInternal, () -> closed, metrics, debugMode);
        this.resultProcessor = new ResultProcessor<>(config, backend, metrics, retryManager, this::submitInternal);
        
        // Initialize batch formation strategy
        this.batchFormationStrategy = new BatchFormationStrategy<>(config, queue, debugMode);
        
        // Initialize tracing helper
        TracingHelper tracingHelper = new TracingHelper(tracingHook, debugMode);
        
        // Initialize batch dispatcher
        this.batchDispatcher = new BatchDispatcher<>(
            config, backend, executor, metrics, resultProcessor,
            dispatchSemaphore, activeBatchCount, tracingHelper, debugMode);
        
        // Initialize submission handler
        this.submissionHandler = new SubmissionHandler<>(
            config, queue, metrics, tracingHelper,
            () -> closed, this::newClosedException);
        
        // Initialize shutdown manager
        this.shutdownManager = new ShutdownManager<>(
            queue, executor, activeBatchCount, backend, resultProcessor, retryManager);
        
        // Start the batch processor
        startBatchProcessor();
    }
    
    /**
     * Submits an item for batch processing without a callback.
     * 
     * <p>This is a convenience method that calls {@link #submit(Object, ItemCallback)} with a null callback.
     * Use this when you only need to check immediate acceptance/rejection and don't need to be notified
     * when the item is processed.
     * 
     * <p>Example:
     * <pre>{@code
     * ItemResult<String> result = batcher.submit("item-1");
     * if (result instanceof ItemResult.Failure<String> failure) {
     *     // Item was rejected (e.g., queue full)
     *     handleRejection(failure.error());
     * }
     * // Item accepted - will be processed in batch later
     * }</pre>
     * 
     * @param item the item to submit
     * @return ItemResult indicating immediate acceptance (SUCCESS) or rejection (FAILURE)
     * @throws IllegalStateException if batcher is closed
     * @throws NullPointerException if item is null
     * @since 0.0.9
     */
    public ItemResult<T> submit(T item) {
        return submit(item, null);
    }

    /**
     * Creates a standard IllegalStateException used when the batcher is closed.
     * Centralizes diagnostic message formatting so all public methods report
     * consistent context (queue depth and active batches).
     */
    private IllegalStateException newClosedException() {
        int queueDepth = queue.size();
        int activeBatches = activeBatchCount != null ? activeBatchCount.get() : 0;
        return new IllegalStateException(
            String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                queueDepth, activeBatches)
        );
    }
    
    /**
     * Submits an item with immediate rejection feedback and optional callback for batch processing result.
     * 
     * <p>This method provides:
     * <ul>
     *   <li><strong>Immediate Rejection</strong>: Returns immediately with ItemResult indicating
     *       acceptance or rejection. If queue is full, returns ItemResult.Failure immediately.</li>
     *   <li><strong>Individual Item Callback</strong>: If item is accepted, the callback (if provided)
     *       fires when this specific item is processed by the backend as part of a batch
     *       (typically 10-50ms after submission, depending on batch size and linger time).
     *       The callback receives the individual item's result, not the full batch result.</li>
     * </ul>
     * 
     * <p><strong>Behavior:</strong>
     * <ul>
     *   <li>If queue is full: Returns ItemResult.Failure immediately, callback is NOT invoked</li>
     *   <li>If item is accepted: Returns ItemResult.Success immediately, callback fires later with this item's result</li>
     *   <li>Items are queued and processed in batches according to BatcherConfig (batchSize, lingerTime)</li>
     *   <li>Callback fires once per item with that item's individual result (success or failure)</li>
     * </ul>
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * // With callback for individual item result
     * ItemResult<MyItem> result = batcher.submit(item, new ItemCallback<MyItem>() {
     *     @Override
     *     public void onResult(ItemResult<MyItem> result) {
     *         if (result instanceof ItemResult.Success<MyItem> success) {
     *             // This specific item processed successfully
     *             MyItem processedItem = success.getItem();
     *             successCounter.increment();
     *         } else if (result instanceof ItemResult.Failure<MyItem> failure) {
     *             // This specific item failed during batch processing
     *             MyItem failedItem = failure.getItem();
     *             failureCounter.increment();
     *             logger.error("Item failed: {}", failure.error().getMessage());
     *         }
     *     }
     * });
     * 
     * // Or using lambda (since ItemCallback is a functional interface)
     * ItemResult<MyItem> result = batcher.submit(item, itemResult -> {
     *     if (itemResult instanceof ItemResult.Success<MyItem> success) {
     *         successCounter.increment();
     *     } else if (itemResult instanceof ItemResult.Failure<MyItem> failure) {
     *         failureCounter.increment();
     *     }
     * });
     * 
     * // Check immediate rejection
     * if (result instanceof ItemResult.Failure<MyItem> failure) {
     *     // Queue was full - item rejected immediately
     *     rejectionCounter.increment();
     *     handleRejection(failure.error());
     * }
     * }</pre>
     * 
     * <p><strong>Example Usage (No Callback - Fire and Forget):</strong>
     * <pre>{@code
     * // Just check immediate rejection, don't care about batch result
     * ItemResult<MyItem> result = batcher.submit(item, null);
     * 
     * if (result instanceof ItemResult.Failure<MyItem> failure) {
     *     // Queue was full - handle rejection
     *     handleRejection(failure.error());
     * }
     * // Item accepted - will be processed in batch later
     * }</pre>
     * 
     * @param item the item to submit
     * @param callback optional callback that receives the item and its individual result when processing completes
     *                 (only invoked if item is accepted). The callback fires once per item with that item's result.
     *                 If null, no callback is invoked.
     * @return ItemResult indicating immediate acceptance (SUCCESS) or rejection (FAILURE)
     * @throws IllegalStateException if batcher is closed
     * @throws NullPointerException if item is null
     * @since 0.0.9
     */
    public ItemResult<T> submit(T item, ItemCallback<T> callback) {
        // Synchronous validation - throw immediately for null/closed
        if (closed) {
            throw newClosedException();
        }
        
        if (item == null) {
            throw new NullPointerException("Item cannot be null");
        }
        
        SubmissionContext<T> context = submissionHandler.submitCommon(item, true, false);
        
        // Handle immediate rejections - return synchronously
        if (context.enqueueResult == EnqueueResult.REJECTED_THRESHOLD || 
            context.enqueueResult == EnqueueResult.REJECTED_FULL) {
            int currentSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            return ItemResult.failure(item, ItemRejectedException.queueFull(currentSize, maxSize));
        } else if (context.enqueueResult == EnqueueResult.INTERRUPTED) {
            return ItemResult.failure(item, new InterruptedException("Interrupted while queuing item"));
        }
        
        // Item accepted - attach callback if provided
        if (callback != null) {
            context.batchFuture.thenAccept(batchResult -> {
                ItemResult<T> itemResult = batchResult.findItemResult(item)
                    .orElseThrow(() -> new IllegalStateException("Item result not found in batch"));
                callback.onResult(itemResult);
            });
        }
        
        return ItemResult.success(item);
    }
    
    /**
     * Submits an item asynchronously, returning a CompletableFuture that completes
     * with the item's processing result.
     * 
     * <p>This method provides an asynchronous API for submission, allowing users
     * to chain CompletableFuture operations:
     * <pre>{@code
     * CompletableFuture<ItemResult<String>> future = batcher.submitAsync("item");
     * future
     *     .thenAccept(result -> {
     *         if (result instanceof ItemResult.Success<String>) {
     *             System.out.println("Success!");
     *         }
     *     })
     *     .exceptionally(throwable -> {
     *         if (throwable instanceof ItemRejectedException) {
     *             // Handle rejection
     *         }
     *         return null;
     *     });
     * }</pre>
     * 
     * <p><strong>Behavior:</strong>
     * <ul>
     *   <li>Returns CompletableFuture immediately (never blocks)</li>
     *   <li>If queue is full: Completes future exceptionally with ItemRejectedException</li>
     *   <li>If item is accepted: Completes future with ItemResult when batch processing completes</li>
     *   <li>The future completes on the batch processing thread</li>
     * </ul>
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * // Chain operations
     * batcher.submitAsync(item)
     *     .thenApply(result -> {
     *         if (result instanceof ItemResult.Success<MyItem>) {
     *             return processSuccess(result.getItem());
     *         } else if (result instanceof ItemResult.Failure<MyItem> failure) {
     *             return handleFailure(failure.error());
     *         }
     *         return null;
     *     })
     *     .thenAccept(processed -> System.out.println("Processed: " + processed));
     * 
     * // Or use with CompletableFuture.allOf()
     * List<CompletableFuture<ItemResult<MyItem>>> futures = items.stream()
     *     .map(batcher::submitAsync)
     *     .toList();
     * CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
     *     .thenRun(() -> System.out.println("All items processed"));
     * }</pre>
     * 
     * @param item the item to submit
     * @return a CompletableFuture that completes with ItemResult when the item is processed,
     *         or completes exceptionally with ItemRejectedException if the item is rejected immediately
     * @throws IllegalStateException if batcher is closed (synchronous check)
     * @throws NullPointerException if item is null (synchronous check)
     * @since 0.0.11
     */
    public CompletableFuture<ItemResult<T>> submitAsync(T item) {
        SubmissionContext<T> context = submissionHandler.submitCommon(item, true, false);
        
        // For rejected items, the future is already completed exceptionally
        // For accepted items, transform BatchResult to ItemResult
        return context.batchFuture.thenApply(batchResult -> {
            ItemResult<T> itemResult = batchResult.findItemResult(item)
                .orElseThrow(() -> new IllegalStateException("Item result not found in batch"));
            return itemResult;
        });
    }
    
    /**
     * Internal method for retries and replays that returns CompletableFuture<BatchResult<T>>.
     * This is used by RetryManager and ResultProcessor which need the old interface.
     * 
     * @param item the item to submit
     * @return a CompletableFuture that completes with the batch result
     */
    private CompletableFuture<BatchResult<T>> submitInternal(T item) {
        if (closed) {
            CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
            future.completeExceptionally(newClosedException());
            return future;
        }
        
        // Tracing hook - use submission handler's tracing helper
        // Note: This is a temporary workaround for submitInternal which needs tracing
        // but doesn't go through submissionHandler. In a future refactor, we could
        // extract this to a shared TracingHelper instance.
        if (tracingHook != null && item != null) {
            try {
                tracingHook.onSubmit(item);
            } catch (Exception e) {
                if (debugMode) {
                    logger.debug("Tracing hook onSubmit failed", e);
                }
            }
        }

        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(item, future);

        // For internal retries/replays we do not apply the public rejection threshold,
        // but we still use a timed offer to avoid blocking indefinitely.
        EnqueueResult enqueueResult = submissionHandler.tryEnqueue(request, false, true);

        if (enqueueResult == EnqueueResult.REJECTED_THRESHOLD || enqueueResult == EnqueueResult.REJECTED_FULL) {
            metrics.recordRequestRejected();
            int currentSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            future.completeExceptionally(ItemRejectedException.queueFull(currentSize, maxSize));
            return future;
        } else if (enqueueResult == EnqueueResult.INTERRUPTED) {
            future.completeExceptionally(new InterruptedException("Interrupted while queuing item"));
            return future;
        }

        metrics.recordRequestSubmitted();
        return future;
    }
    
    /**
     * Gets the current number of items in the queue waiting for batch processing.
     * 
     * <p>This method is useful for:
     * <ul>
     *   <li>Monitoring queue depth for metrics/dashboards</li>
     *   <li>Debugging and troubleshooting</li>
     * </ul>
     * 
     * <p><strong>Note:</strong> This is a snapshot of the queue depth at the time
     * of the call. The actual queue depth may change immediately after this call
     * returns due to concurrent submissions and batch processing.
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * // Monitor queue depth
     * int queueDepth = batcher.getQueueDepth();
     * if (queueDepth > 1000) {
     *     log.warn("Queue depth is high: {}", queueDepth);
     * }
     * }</pre>
     * 
     * @return current queue depth (number of items waiting)
     * @since 0.0.5
     */
    public int getQueueDepth() {
        return queue.size();
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
        List<PendingRequest<T>> batch = batchFormationStrategy.formBatch();
        
        if (!batch.isEmpty()) {
            if (debugMode) {
                logger.debug("Dispatching batch of size: {}", batch.size());
            }
            dispatchBatch(batch);
        }
    }
    
    private void dispatchBatch(List<PendingRequest<T>> batch) {
        batchDispatcher.dispatchBatch(batch);
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
        shutdownManager.shutdown();
    }
    
    /**
     * Waits for all queued items and in-flight batches to complete.
     * 
     * <p>This method provides a way to gracefully wait for all processing to complete
     * before closing the batcher. It waits for:
     * <ul>
     *   <li>All items in the queue to be processed</li>
     *   <li>All in-flight batches to complete</li>
     * </ul>
     * 
     * <p>This is useful in scenarios where you want to ensure all items are processed
     * before shutting down, such as in test teardown or application shutdown.
     * 
     * <p>Example:
     * <pre>{@code
     * // Wait for all items to complete before closing
     * batcher.awaitCompletion(5, TimeUnit.SECONDS);
     * batcher.close();
     * }</pre>
     * 
     * <p>Note: This method does not prevent new submissions. If you want to stop
     * accepting new items, call {@link #close()} first, then call this method.
     * However, calling this method before close() is also valid and will wait
     * for items submitted up to that point.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return true if all items completed within the timeout, false if timeout was reached
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @since 0.0.7
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        return shutdownManager.awaitCompletion(timeout, unit, closed);
    }
    
    /**
     * Gets the MeterRegistry used for metrics.
     * 
     * <p>This provides direct access to the underlying Micrometer registry
     * for advanced use cases. For most use cases, consider using
     * {@link #getMetricsProvider()} instead, which provides a simpler,
     * domain-specific API.
     * 
     * @return the meter registry
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
    
    /**
     * Returns a MetricsProvider that provides real-time access to batcher metrics.
     * 
     * <p>The MetricsProvider offers convenient access to key metrics for:
     * <ul>
     *   <li>Adaptive batch sizing based on failure rate</li>
     *   <li>Circuit breaker patterns</li>
     *   <li>Auto-scaling decisions</li>
     *   <li>Health monitoring</li>
     * </ul>
     * 
     * <p>Example usage:
     * <pre>{@code
     * MetricsProvider metrics = batcher.getMetricsProvider();
     * 
     * // Health check
     * boolean isHealthy = metrics.getFailureRate() < 0.05 
     *     && metrics.getQueueDepth() < 100;
     * }</pre>
     * 
     * <p>The returned MetricsProvider is a live view of current metrics.
     * Each method call queries the underlying metrics in real-time.
     * 
     * @return a MetricsProvider instance providing real-time metrics
     * @since 0.0.3
     */
    public MetricsProvider getMetricsProvider() {
        return metrics.getMetricsProvider();
    }
    
    /**
     * Checks if the batcher is closed.
     * 
     * <p>Once closed, the batcher will reject new submissions and will not process
     * any new batches. Items already in the queue may still be processed during
     * the shutdown process.
     * 
     * <p>This method is thread-safe and can be called from any thread.
     * 
     * @return true if the batcher is closed, false otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns a lightweight diagnostics view of the current batcher state.
     *
     * <p>The diagnostics view is read-only and safe to call concurrently from
     * any thread. It is intended for use in health checks, dashboards, and
     * operational tooling.
     *
     * @return diagnostics view exposing current state
     * @since 0.0.3
     */
    public BatcherDiagnostics diagnostics() {
        return new DefaultBatcherDiagnostics<>(closed, config, queue);
    }
    
    /**
     * Gets the configuration used by this batcher.
     * 
     * <p>This method provides read-only access to the batcher's configuration.
     * The returned config reflects the configuration used by this batcher.
     * 
     * @return the batcher configuration
     * @since 0.0.5
     */
    public BatcherConfig getConfig() {
        return config;
    }
}
