package com.vajrapulse.vortex;

import com.vajrapulse.vortex.backpressure.BackpressureProvider;
import com.vajrapulse.vortex.backpressure.BackpressureStrategy;
import com.vajrapulse.vortex.backpressure.BackpressureContext;
import com.vajrapulse.vortex.backpressure.BackpressureResult;
import com.vajrapulse.vortex.backpressure.BackpressureAction;
import com.vajrapulse.vortex.backpressure.LifecycleAwareStrategy;
import com.vajrapulse.vortex.backpressure.DropStrategy;
import com.vajrapulse.vortex.backpressure.RejectStrategy;
import com.vajrapulse.vortex.backpressure.OverflowStrategy;
import com.vajrapulse.vortex.backpressure.BackpressureLevelCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

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
    
    // Backpressure support (optional)
    private final BackpressureProvider backpressureProvider;
    private final BackpressureStrategy<T> backpressureStrategy;
    private final BackpressureLevelCache backpressureCache;
    private final ScheduledExecutorService backpressureMonitor;
    private volatile boolean backpressureActive = false;
    
    // Concurrent dispatch limiting (optional)
    private final Semaphore dispatchSemaphore;
    private final int maxConcurrentBatches;
    private final AtomicInteger activeBatchCount;
    
    private volatile boolean closed = false;
    
    // Dynamic configuration (mutable, thread-safe)
    private volatile int currentBatchSize;
    private volatile Duration currentLingerTime;
    
    // Cached configuration for performance
    private final boolean debugMode;
    private final BatchTracingHook tracingHook;
    
    // Helper classes
    private final MetricsManager metrics;
    private final RetryManager<T> retryManager;
    private final ResultProcessor<T> resultProcessor;
    
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
     * <p>Backpressure is configured via {@link BatcherConfig.Builder#backpressureProvider(BackpressureProvider)}
     * and {@link BatcherConfig.Builder#backpressureStrategy(BackpressureStrategy)}.
     * 
     * <p>Example:
     * <pre>{@code
     * BatcherConfig config = BatcherConfig.builder()
     *     .batchSize(10)
     *     .lingerTime(Duration.ofMillis(100))
     *     .backpressureProvider(provider)
     *     .backpressureStrategy(strategy)
     *     .build();
     * 
     * MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, meterRegistry);
     * }</pre>
     * 
     * @param backend the backend implementation
     * @param config the batcher configuration (may contain backpressure provider and strategy)
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
        this.backpressureProvider = config.getBackpressureProvider();
        this.backpressureStrategy = config.getBackpressureStrategy();
        
        // Initialize backpressure cache if provider is configured
        this.backpressureCache = (backpressureProvider != null) 
            ? new BackpressureLevelCache(backpressureProvider, config.getBackpressureCacheTtl())
            : null;
        
        // Initialize concurrent dispatch limiting
        this.maxConcurrentBatches = config.getMaxConcurrentBatches();
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
        
        // Initialize dynamic config from static config
        this.currentBatchSize = config.getBatchSize();
        this.currentLingerTime = config.getLingerTime();
        
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
        
        this.retryManager = new RetryManager<>(config, executor, this::submit, () -> closed, metrics, debugMode);
        this.resultProcessor = new ResultProcessor<>(config, backend, metrics, retryManager, this::submit, debugMode);
        
        // Start backpressure monitoring if lifecycle-aware strategy is provided
        if (backpressureStrategy instanceof LifecycleAwareStrategy) {
            this.backpressureMonitor = startBackpressureMonitoring();
        } else {
            this.backpressureMonitor = null;
        }
        
        // Start the batch processor
        startBatchProcessor();
    }
    
    /**
     * Creates a MicroBatcher optimized for high-throughput scenarios.
     * 
     * <p>This factory method creates a batcher with:
     * <ul>
     *   <li>Large batch size (100 items)</li>
     *   <li>Longer linger time (500ms)</li>
     *   <li>Large queue size (500 items)</li>
     * </ul>
     * 
     * <p>Use when:
     * <ul>
     *   <li>Maximum throughput is required</li>
     *   <li>Latency up to 500ms is acceptable</li>
     *   <li>Processing large volumes efficiently</li>
     * </ul>
     * 
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li>Throughput: High (optimized for maximum items/second)</li>
     *   <li>Latency: Higher (up to 500ms per batch)</li>
     *   <li>Memory: Higher (larger queue and batches)</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>{@code
     * MicroBatcher<String> batcher = MicroBatcher.forHighThroughput(backend, registry);
     * }</pre>
     * 
     * @param <T> the type of request elements
     * @param backend the backend implementation
     * @param meterRegistry the meter registry for metrics
     * @return a new MicroBatcher optimized for high throughput
     * @since 0.0.5
     */
    public static <T> MicroBatcher<T> forHighThroughput(Backend<T> backend, MeterRegistry meterRegistry) {
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(100)
            .lingerTime(Duration.ofMillis(500))
            .maxQueueSize(500)
            .build();
        return new MicroBatcher<>(backend, config, meterRegistry);
    }
    
    /**
     * Creates a MicroBatcher optimized for low-latency scenarios.
     * 
     * <p>This factory method creates a batcher with:
     * <ul>
     *   <li>Small batch size (5 items)</li>
     *   <li>Short linger time (10ms)</li>
     *   <li>Small queue size (20 items)</li>
     * </ul>
     * 
     * <p>Use when:
     * <ul>
     *   <li>Latency is critical (&lt; 50ms)</li>
     *   <li>Throughput is less important</li>
     *   <li>Real-time or near-real-time processing</li>
     * </ul>
     * 
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li>Throughput: Lower (smaller batches)</li>
     *   <li>Latency: Low (typically &lt; 50ms per batch)</li>
     *   <li>Memory: Lower (smaller queue and batches)</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>{@code
     * MicroBatcher<String> batcher = MicroBatcher.forLowLatency(backend, registry);
     * }</pre>
     * 
     * @param <T> the type of request elements
     * @param backend the backend implementation
     * @param meterRegistry the meter registry for metrics
     * @return a new MicroBatcher optimized for low latency
     * @since 0.0.5
     */
    public static <T> MicroBatcher<T> forLowLatency(Backend<T> backend, MeterRegistry meterRegistry) {
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(10))
            .maxQueueSize(20)
            .build();
        return new MicroBatcher<>(backend, config, meterRegistry);
    }
    
    /**
     * Creates a MicroBatcher optimized for balanced scenarios (default).
     * 
     * <p>This factory method creates a batcher with:
     * <ul>
     *   <li>Medium batch size (20 items)</li>
     *   <li>Medium linger time (100ms)</li>
     *   <li>Medium queue size (50 items)</li>
     * </ul>
     * 
     * <p>Use when:
     * <ul>
     *   <li>Balancing latency and throughput</li>
     *   <li>General-purpose batching</li>
     *   <li>Most common use case</li>
     * </ul>
     * 
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li>Throughput: Medium (good balance)</li>
     *   <li>Latency: Medium (typically 50-200ms per batch)</li>
     *   <li>Memory: Medium (reasonable queue and batch sizes)</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>{@code
     * MicroBatcher<String> batcher = MicroBatcher.forBalanced(backend, registry);
     * }</pre>
     * 
     * @param <T> the type of request elements
     * @param backend the backend implementation
     * @param meterRegistry the meter registry for metrics
     * @return a new MicroBatcher optimized for balanced performance
     * @since 0.0.5
     */
    public static <T> MicroBatcher<T> forBalanced(Backend<T> backend, MeterRegistry meterRegistry) {
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(20)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(50)
            .build();
        return new MicroBatcher<>(backend, config, meterRegistry);
    }
    
    /**
     * Creates a MicroBatcher optimized for resilient scenarios with retry support.
     * 
     * <p>This factory method creates a batcher with:
     * <ul>
     *   <li>Medium batch size (10 items)</li>
     *   <li>Medium linger time (100ms)</li>
     *   <li>Retry support (3 retries with 100ms delay)</li>
     *   <li>Retries transient errors (IOException, TimeoutException)</li>
     * </ul>
     * 
     * <p>Use when:
     * <ul>
     *   <li>Dealing with unreliable backends</li>
     *   <li>Network calls that may fail transiently</li>
     *   <li>Resilience is more important than throughput</li>
     * </ul>
     * 
     * <p><strong>Performance Characteristics:</strong>
     * <ul>
     *   <li>Throughput: Medium (retries may reduce effective throughput)</li>
     *   <li>Latency: Higher (retries add delay)</li>
     *   <li>Reliability: High (automatic retry for transient failures)</li>
     * </ul>
     * 
     * <p>Example:
     * <pre>{@code
     * MicroBatcher<String> batcher = MicroBatcher.forResilient(
     *     backend, 
     *     registry,
     *     e -> e instanceof IOException || e instanceof TimeoutException
     * );
     * }</pre>
     * 
     * @param <T> the type of request elements
     * @param backend the backend implementation
     * @param meterRegistry the meter registry for metrics
     * @param retryableErrorPredicate predicate to determine which errors should be retried
     * @return a new MicroBatcher optimized for resilience
     * @since 0.0.5
     */
    public static <T> MicroBatcher<T> forResilient(
            Backend<T> backend,
            MeterRegistry meterRegistry,
            java.util.function.Predicate<Throwable> retryableErrorPredicate) {
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxRetries(3)
            .retryDelay(Duration.ofMillis(100))
            .retryableErrorPredicate(retryableErrorPredicate)
            .maxQueueSize(30)
            .build();
        return new MicroBatcher<>(backend, config, meterRegistry);
    }
    
    /**
     * Submits a request to be batched and dispatched.
     * 
     * <p>This method is thread-safe and can be called from multiple threads concurrently.
     * The request will be added to the batching queue and processed according to the
     * configured batch size and linger time settings.
     * 
     * <p>If the batcher is closed, this method will throw {@link IllegalStateException}.
     * If backpressure is detected, the strategy will determine how to handle the item
     * (ACCEPT, REJECT, or DROP). If the queue is full, the returned future will complete
     * exceptionally with {@link RejectedExecutionException}.
     * 
     * @param data the request data
     * @return a CompletableFuture that completes with the batch result
     * @throws IllegalStateException if the batcher is closed
     */
    public CompletableFuture<BatchResult<T>> submit(T data) {
        if (closed) {
            throw new IllegalStateException(
                String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                    queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
            );
        }
        
        // Check backpressure FIRST (before any other work)
        if (backpressureProvider != null && backpressureStrategy != null) {
            try {
                double backpressure = backpressureProvider.getBackpressureLevel();
                
                // Validate backpressure level
                if (Double.isNaN(backpressure) || backpressure < 0.0 || backpressure > 1.0) {
                    metrics.recordBackpressureInvalidLevel();
                    if (debugMode) {
                        logger.warn("Invalid backpressure level: {}, defaulting to 0.0", backpressure);
                    }
                    backpressure = 0.0;
                }
                
                BackpressureContext<T> context = new BackpressureContext<>(
                    data, backpressure, backpressureProvider
                );
                
                BackpressureResult<T> result = backpressureStrategy.handle(context);
                
                return switch (result.action()) {
                    case ACCEPT -> proceedWithSubmission(data);
                    case REJECT -> {
                        metrics.recordBackpressureRejected();
                        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
                        future.completeExceptionally(result.reason());
                        yield future;
                    }
                    case DROP -> {
                        metrics.recordBackpressureDropped();
                        // Return success but don't actually process
                        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
                        future.complete(new BatchResult<>(
                            List.of(new SuccessEvent<>(data)),
                            List.of()
                        ));
                        yield future;
                    }
                };
            } catch (Exception e) {
                // Fail-safe: if backpressure check fails, proceed normally
                if (debugMode) {
                    logger.error("Backpressure check failed, proceeding with submission", e);
                }
                return proceedWithSubmission(data);
            }
        }
        
        // No backpressure - normal flow
        return proceedWithSubmission(data);
    }
    
    /**
     * Proceeds with normal submission flow (after backpressure check).
     * 
     * @param data the request data
     * @return a CompletableFuture that completes with the batch result
     */
    private CompletableFuture<BatchResult<T>> proceedWithSubmission(T data) {
        if (tracingHook != null) {
            try {
                tracingHook.onSubmit(data);
            } catch (Exception e) {
                if (debugMode) {
                    logger.debug("Tracing hook onSubmit failed", e);
                }
            }
        }
        
        metrics.recordRequestSubmitted();
        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(data, future);
        
        try {
            if (!queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                metrics.recordRequestRejected();
                future.completeExceptionally(new RejectedExecutionException("Queue is full"));
                return future;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Gets the current number of items in the queue waiting for batch processing.
     * 
     * <p>This method is useful for:
     * <ul>
     *   <li>Monitoring queue depth for metrics/dashboards</li>
     *   <li>Creating QueueDepthBackpressureProvider</li>
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
     * 
     * // Use with QueueDepthBackpressureProvider
     * Supplier<Integer> queueDepthSupplier = () -> batcher.getQueueDepth();
     * BackpressureProvider provider = new QueueDepthBackpressureProvider(
     *     queueDepthSupplier,
     *     maxQueueSize
     * );
     * }</pre>
     * 
     * @return current queue depth (number of items waiting)
     * @since 0.0.5
     */
    public int getQueueDepth() {
        return queue.size();
    }
    
    /**
     * Submits an item synchronously and returns immediately with the result.
     * 
     * <p>This method checks backpressure <strong>before</strong> queuing the item.
     * The result indicates whether the item was accepted or rejected:
     * <ul>
     *   <li><strong>SUCCESS</strong>: Item was accepted and queued successfully.
     *       The item will be processed in a batch later (via batch processing).</li>
     *   <li><strong>REJECTED</strong>: Item was rejected due to backpressure
     *       (e.g., queue is full). The item will NOT be processed.</li>
     * </ul>
     * 
     * <p><strong>Important Notes:</strong>
     * <ul>
     *   <li>This method does <strong>NOT</strong> wait for batch processing.
     *       It only checks if the item can be queued.</li>
     *   <li>If you need to know the batch processing result (success/failure),
     *       use {@link #submitWithCallback(Object, java.util.function.BiConsumer)} instead.</li>
     *   <li>Rejections happen immediately based on backpressure threshold,
     *       not based on batch processing capacity.</li>
     * </ul>
     * 
     * <p><strong>Note on Queue Depth Check Race Condition:</strong>
     * There is a small race condition window between the queue depth check
     * ({@code getQueueDepth()}) and the actual queue offer operation ({@code queue.offer()}).
     * If the queue fills between these operations (due to concurrent submissions),
     * the offer will fail and the item will be rejected, even though the initial
     * check indicated space was available. This is handled gracefully by checking
     * the return value of {@code queue.offer()} and returning a rejection if it fails.
     * This behavior is acceptable for most use cases, as it provides natural
     * backpressure when the queue is near capacity.
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * ItemResult<MyItem> result = batcher.submitSync(item);
     * 
     * if (result instanceof ItemResult.Success<MyItem>) {
     *     // Item queued successfully - will be processed in batch later
     *     // Use submitWithCallback() if you need batch processing result
     * } else if (result instanceof ItemResult.Failure<MyItem> failure) {
     *     // Item rejected due to backpressure
     *     // Handle rejection (retry, log, etc.)
     *     log.warn("Item rejected: {}", failure.error().getMessage());
     * }
     * }</pre>
     * 
     * <p><strong>Integration with Load Testing Frameworks:</strong>
     * <pre>{@code
     * // In load testing task
     * ItemResult<Item> result = batcher.submitSync(item);
     * 
     * if (result instanceof ItemResult.Failure<Item>) {
     *     // Return failure to load testing framework
     *     // Framework will see this as a failure and adjust TPS
     *     return TaskResult.failure(result.error());
     * }
     * 
     * // Item accepted - return success
     * // Use submitWithCallback() to track batch processing results separately
     * return TaskResult.success();
     * }</pre>
     * 
     * @param item the item to submit
     * @return result indicating acceptance (SUCCESS) or rejection (FAILURE)
     * @throws IllegalStateException if batcher is closed
     * @throws NullPointerException if item is null
     * @since 0.0.5
     */
    public ItemResult<T> submitSync(T item) {
        if (closed) {
            throw new IllegalStateException(
                String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                    queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
            );
        }
        
        // Check backpressure synchronously
        if (backpressureProvider != null && backpressureStrategy != null) {
            try {
                double backpressure = backpressureCache.getBackpressureLevel();
                
                // Validate backpressure level
                if (Double.isNaN(backpressure) || backpressure < 0.0 || backpressure > 1.0) {
                    metrics.recordBackpressureInvalidLevel();
                    if (debugMode) {
                        logger.warn("Invalid backpressure level: {}, defaulting to 0.0", backpressure);
                    }
                    backpressure = 0.0;
                }
                
                BackpressureContext<T> context = new BackpressureContext<>(
                    item, backpressure, backpressureProvider
                );
                BackpressureResult<T> result = backpressureStrategy.handle(context);
                
                if (result.action() == BackpressureAction.REJECT) {
                    // Reject immediately
                    metrics.recordBackpressureRejected();
                    return ItemResult.failure(item, result.reason());
                } else if (result.action() == BackpressureAction.DROP) {
                    // Drop silently - return success but don't actually queue
                    metrics.recordBackpressureDropped();
                    return ItemResult.success(item);
                }
                // ACCEPT - continue to queue capacity check
            } catch (Exception e) {
                // Fail-safe: if backpressure check fails, proceed normally
                metrics.recordBackpressureCheckFailure();
                if (debugMode) {
                    logger.error("Backpressure check failed, proceeding with submission", e);
                }
                // Continue to queue capacity check
            }
        }
        
        // Accept and queue (non-blocking offer)
        // Note: We rely on queue.offer() return value directly, which is atomic.
        // This eliminates the race condition window from checking size separately.
        PendingRequest<T> request = new PendingRequest<>(item, new CompletableFuture<>());
        if (queue.offer(request)) {
            metrics.recordRequestSubmitted();
            return ItemResult.success(item);
        } else {
            // Queue is full - reject immediately
            int currentQueueSize = queue.size();
            int maxQueueSize = config.getMaxQueueSize();
            metrics.recordRequestRejected();
            return ItemResult.failure(item, new RejectedExecutionException(
                String.format("Queue full: %d/%d", currentQueueSize, maxQueueSize)
            ));
        }
    }
    
    /**
     * Submits an item with a callback that fires when batch processing completes.
     * 
     * <p>This method accepts the item and queues it for batch processing.
     * The callback will be invoked with the result when:
     * <ul>
     *   <li><strong>Immediate Rejection</strong>: If the item is rejected due to
     *       backpressure (queue full), the callback fires immediately with a
     *       {@link ItemResult.Failure} containing a {@link java.util.concurrent.RejectedExecutionException}
     *       or {@link com.vajrapulse.vortex.backpressure.BackpressureException}.</li>
     *   <li><strong>Batch Processing</strong>: If the item is accepted, the callback
     *       fires when the batch containing this item is processed (typically 10-50ms
     *       after submission, depending on batch size and linger time).</li>
     * </ul>
     * 
     * <p><strong>Callback Timing:</strong>
     * <ul>
     *   <li><strong>Immediate</strong>: If item rejected (backpressure >= threshold)
     *       - Callback fires synchronously or on submission thread</li>
     *   <li><strong>After Batch Processing</strong>: If item accepted
     *       - Callback fires asynchronously when batch completes (typically 10-50ms)</li>
     * </ul>
     * 
     * <p><strong>Important Notes:</strong>
     * <ul>
     *   <li>The callback may fire on a different thread (batch processing thread).</li>
     *   <li>If you need immediate rejection feedback, use {@link #submitSync(Object)}
     *       first to check if item will be rejected.</li>
     *   <li>For load testing frameworks, you may want to use `submitSync()` to get
     *       immediate rejection feedback, then use `submitWithCallback()` for
     *       tracking batch processing results.</li>
     * </ul>
     * 
     * <p><strong>Example Usage:</strong>
     * <pre>{@code
     * batcher.submitWithCallback(item, (submittedItem, result) -> {
     *     if (result instanceof ItemResult.Success<MyItem>) {
     *         // Item processed successfully in batch
     *         successCounter.increment();
     *     } else if (result instanceof ItemResult.Failure<MyItem> failure) {
     *         // Item failed (either rejected or batch processing failed)
     *         if (failure.error() instanceof RejectedExecutionException) {
     *             // Immediate rejection due to backpressure
     *             rejectionCounter.increment();
     *         } else {
     *             // Batch processing failure
     *             failureCounter.increment();
     *         }
     *     }
     * });
     * }</pre>
     * 
     * <p><strong>Integration with Load Testing Frameworks:</strong>
     * <pre>{@code
     * // In load testing task
     * // Use submitSync() for immediate rejection feedback
     * ItemResult<Item> syncResult = batcher.submitSync(item);
     * 
     * if (syncResult instanceof ItemResult.Failure<Item>) {
     *     // Immediate rejection - return failure to framework
     *     return TaskResult.failure(syncResult.error());
     * }
     * 
     * // Item accepted - use callback for batch processing results
     * batcher.submitWithCallback(item, (submittedItem, batchResult) -> {
     *     // Track batch processing results separately
     *     // (for metrics, not for framework feedback)
     *     if (batchResult instanceof ItemResult.Success<Item>) {
     *         batchSuccessCounter.increment();
     *     } else {
     *         batchFailureCounter.increment();
     *     }
     * });
     * 
     * // Return success - item was accepted
     * return TaskResult.success();
     * }</pre>
     * 
     * @param item the item to submit
     * @param callback callback that receives the item and result when processing completes
     * @return CompletableFuture that completes when the callback finishes
     * @throws IllegalStateException if the batcher is closed
     * @throws NullPointerException if item or callback is null
     * @since 0.0.5
     */
    public CompletableFuture<Void> submitWithCallback(T item, java.util.function.BiConsumer<T, ItemResult<T>> callback) {
        if (callback == null) {
            throw new NullPointerException("Callback cannot be null");
        }
        if (closed) {
            throw new IllegalStateException(
                String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                    queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
            );
        }
        
        ItemResult<T> checkResult = checkRejection(item);
        if (checkResult instanceof ItemResult.Failure<T> failure) {
            // Immediate rejection - invoke callback immediately
            try {
                callback.accept(item, failure);
                return CompletableFuture.completedFuture(null);
            } catch (Exception e) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(e);
                return future;
            }
        }
        
        // Item would be accepted - now actually submit it
        CompletableFuture<BatchResult<T>> future = submit(item);
        return future.thenAccept(result -> {
            ItemResult<T> itemResult = result.findItemResult(item)
                .orElseThrow(() -> new IllegalStateException("Item result not found for submitted item"));
            callback.accept(item, itemResult);
        });
    }
    
    /**
     * Checks if an item would be rejected (without actually queuing it).
     * Used internally by submitWithCallback() to check rejection before submitting.
     * 
     * <p><b>Note on Queue Depth Check Race Condition:</b>
     * This method performs a non-atomic check of queue depth. There is a small
     * race condition window between the queue depth check and the actual queue
     * offer operation in {@link #submitSync(Object)}. This is acceptable for
     * most use cases as it provides natural backpressure when the queue is near
     * capacity. The actual offer operation in {@code submitSync()} handles
     * failures gracefully.
     * 
     * @param item the item to check
     * @return ItemResult indicating if item would be accepted or rejected
     */
    private ItemResult<T> checkRejection(T item) {
        // Check backpressure synchronously
        if (backpressureProvider != null && backpressureStrategy != null) {
            try {
                double backpressure = backpressureCache.getBackpressureLevel();
                
                // Validate backpressure level
                if (Double.isNaN(backpressure) || backpressure < 0.0 || backpressure > 1.0) {
                    metrics.recordBackpressureInvalidLevel();
                    if (debugMode) {
                        logger.warn("Invalid backpressure level: {}, defaulting to 0.0", backpressure);
                    }
                    backpressure = 0.0;
                }
                
                BackpressureContext<T> context = new BackpressureContext<>(
                    item, backpressure, backpressureProvider
                );
                BackpressureResult<T> result = backpressureStrategy.handle(context);
                
                if (result.action() == BackpressureAction.REJECT) {
                    return ItemResult.failure(item, result.reason());
                } else if (result.action() == BackpressureAction.DROP) {
                    // DROP is treated as success for immediate check
                    return ItemResult.success(item);
                }
                // ACCEPT - continue to queue capacity check
            } catch (Exception e) {
                // Fail-safe: if backpressure check fails, proceed normally
                metrics.recordBackpressureCheckFailure();
                if (debugMode) {
                    logger.error("Backpressure check failed, proceeding with submission", e);
                }
                // Continue to queue capacity check
            }
        }
        
        // Note: We don't check queue capacity here since this is just a check method.
        // The actual submission methods (submitSync, submit) will handle queue capacity
        // by checking queue.offer() return value, which is atomic.
        // This method is used by submitWithCallback to determine immediate rejection
        // before actually submitting, so we skip the queue check here.
        
        // Would be accepted (queue capacity will be checked during actual submission)
        return ItemResult.success(item);
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
        // Pre-size batch list to avoid resizing
        int batchSize = currentBatchSize;
        List<PendingRequest<T>> batch = new ArrayList<>(batchSize);
        
        Duration lingerTime = currentLingerTime;
        long lingerTimeNanos = lingerTime.toNanos();
        
        // Wait for first item with timeout based on linger time
        PendingRequest<T> first = queue.poll(lingerTime.toMillis(), TimeUnit.MILLISECONDS);
        if (first == null) {
            return;
        }
        
        batch.add(first);
        
        if (debugMode) {
            logger.debug("Starting batch formation, first item: {}", first.getData());
        }
        
        // Collect up to batchSize items, respecting linger time
        long deadline = System.nanoTime() + lingerTimeNanos;
        while (batch.size() < batchSize) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                if (debugMode) {
                    logger.debug("Linger time elapsed, batch size: {}", batch.size());
                }
                break;
            }
            
            // Convert nanos to millis directly (optimization: avoid Duration object creation)
            long remainingMillis = Math.max(1, remainingNanos / 1_000_000);
            PendingRequest<T> next = queue.poll(remainingMillis, TimeUnit.MILLISECONDS);
            if (next == null) {
                if (debugMode) {
                    logger.debug("Timeout waiting for next item, batch size: {}", batch.size());
                }
                break;
            }
            batch.add(next);
            if (debugMode) {
                logger.debug("Added item to batch, current size: {}, queue depth: {}", 
                    batch.size(), queue.size());
            }
        }
        
        if (!batch.isEmpty()) {
            if (debugMode) {
                logger.debug("Dispatching batch of size: {}", batch.size());
            }
            dispatchBatch(batch);
        }
    }
    
    private void dispatchBatch(List<PendingRequest<T>> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        // Try to acquire permit if limit is configured
        boolean acquired = true;
        if (dispatchSemaphore != null) {
            acquired = dispatchSemaphore.tryAcquire();
            if (!acquired) {
                // Can't dispatch - too many concurrent batches
                metrics.recordDispatchRejected();
                if (debugMode) {
                    logger.debug("Batch rejected: too many concurrent batches (limit: {})", maxConcurrentBatches);
                }
                handleDispatchRejection(batch);
                return;
            }
        }
        
        // Build data list once so it can be reused for dispatch, metrics, and tracing
        List<T> dataList = new ArrayList<>(batch.size());
        for (PendingRequest<T> req : batch) {
            dataList.add(req.getData());
        }
        
        if (tracingHook != null) {
            try {
                tracingHook.onBatchDispatchStart(dataList);
            } catch (Exception e) {
                if (debugMode) {
                    logger.debug("Tracing hook onBatchDispatchStart failed", e);
                }
            }
        }
        
        metrics.recordBatchDispatched();
        
        // Calculate average wait time inline (optimization: avoid stream overhead)
        if (debugMode) {
            long totalWait = 0;
            long now = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                totalWait += now - req.getTimestamp();
            }
            long avgWaitTime = totalWait / batch.size();
            logger.debug("Dispatching batch: size={}, avgWaitTimeNs={}", batch.size(), avgWaitTime);
        }
        
        metrics.recordBatchSize(batch.size());
        
        Timer.Sample sample = metrics.startBatchDispatchTimer();
        
        // Record per-item batch size and queue wait time if enabled
        if (config.isPerItemMetrics()) {
            metrics.recordItemBatchSize(batch.size());
            
            // Record queue wait time for each item (from submit to batch dispatch start)
            long dispatchStartTime = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                long queueWaitTime = dispatchStartTime - req.getTimestamp();
                metrics.recordQueueWaitTime(queueWaitTime);
            }
        }
        
        // Execute backend dispatch on a virtual thread
        try {
            executor.submit(() -> {
                // Update active batch count after successful submission
                if (activeBatchCount != null) {
                    activeBatchCount.incrementAndGet();
                }
                try {
                    if (debugMode) {
                        logger.debug("Calling backend.dispatch() for batch of size: {}", dataList.size());
                    }
                    BatchResult<T> result = backend.dispatch(dataList);
                    metrics.recordBatchDispatchLatency(sample);
                    if (tracingHook != null) {
                        try {
                            tracingHook.onBatchDispatchSuccess(dataList, result);
                        } catch (Exception e) {
                            if (debugMode) {
                                logger.debug("Tracing hook onBatchDispatchSuccess failed", e);
                            }
                        }
                    }
                    if (debugMode) {
                        logger.debug("Backend dispatch completed: successes={}, failures={}", 
                            result.getSuccesses().size(), result.getFailures().size());
                    }
                    resultProcessor.processResults(batch, result);
                } catch (Exception e) {
                    metrics.recordBatchDispatchLatency(sample);
                    if (tracingHook != null) {
                        try {
                            tracingHook.onBatchDispatchFailure(dataList, e);
                        } catch (Exception hookError) {
                            if (debugMode) {
                                logger.debug("Tracing hook onBatchDispatchFailure failed", hookError);
                            }
                        }
                    }
                    if (debugMode) {
                        logger.debug("Backend dispatch failed", e);
                    }
                    resultProcessor.processFailure(batch, e);
                } finally {
                    // Release permit when done
                    if (dispatchSemaphore != null) {
                        dispatchSemaphore.release();
                    }
                    // Update active batch count
                    if (activeBatchCount != null) {
                        activeBatchCount.decrementAndGet();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor rejected - release permit (activeBatchCount was never incremented)
            if (dispatchSemaphore != null) {
                dispatchSemaphore.release();
            }
            if (debugMode) {
                logger.debug("Executor rejected batch dispatch", e);
            }
            handleDispatchRejection(batch);
        }
    }
    
    /**
     * Handles rejection of a batch due to concurrent dispatch limit.
     * 
     * <p>When a batch cannot be dispatched due to the concurrent batch limit,
     * the items in the batch are rejected individually. Each item's future
     * (if using submit()) or callback (if using submitWithCallback()) will
     * be notified of the rejection.
     * 
     * @param batch the batch that was rejected
     */
    private void handleDispatchRejection(List<PendingRequest<T>> batch) {
        Exception rejectionError = new RejectedExecutionException(
            "Batch rejected: too many concurrent batches (limit: " + maxConcurrentBatches + ")");
        
        for (PendingRequest<T> request : batch) {
            CompletableFuture<BatchResult<T>> future = request.getFuture();
            if (future != null && !future.isDone()) {
                future.completeExceptionally(rejectionError);
            }
        }
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
    /**
     * Starts backpressure monitoring for lifecycle-aware strategies.
     * 
     * @return the ScheduledExecutorService used for monitoring
     */
    private ScheduledExecutorService startBackpressureMonitoring() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "vortex-backpressure-monitor");
                t.setDaemon(true);
                return t;
            }
        );
        
        // Get monitoring interval from config (defaults to 100ms)
        long intervalMs = config.getBackpressureMonitorInterval().toMillis();
        if (intervalMs <= 0) {
            if (debugMode) {
                logger.warn("Invalid backpressure monitor interval: {}ms, using default 100ms", intervalMs);
            }
            intervalMs = 100;
        }
        
        monitor.scheduleAtFixedRate(() -> {
            if (backpressureProvider != null && 
                backpressureStrategy instanceof LifecycleAwareStrategy) {
                try {
                    double level = backpressureCache.getBackpressureLevel();
                    double threshold = getBackpressureThreshold();
                    
                    // Validate level
                    if (Double.isNaN(level) || level < 0.0 || level > 1.0) {
                        if (debugMode) {
                            logger.warn("Invalid backpressure level in monitor: {}", level);
                        }
                        return;
                    }
                    
                    // Validate threshold
                    if (Double.isNaN(threshold) || threshold < 0.0 || threshold > 1.0) {
                        if (debugMode) {
                            logger.warn("Invalid backpressure threshold in monitor: {}", threshold);
                        }
                        return;
                    }
                    
                    boolean isActive = level >= threshold;
                    
                    if (!backpressureActive && isActive) {
                        // Entering backpressure
                        backpressureActive = true;
                        try {
                            ((LifecycleAwareStrategy<T>) backpressureStrategy)
                                .onBackpressureEntered(backpressureProvider);
                            if (debugMode) {
                                logger.debug("Backpressure entered: level={}, threshold={}", level, threshold);
                            }
                        } catch (Exception e) {
                            if (debugMode) {
                                logger.error("Error in onBackpressureEntered callback", e);
                            }
                            // Continue monitoring despite callback error
                        }
                    } else if (backpressureActive && !isActive) {
                        // Exiting backpressure
                        backpressureActive = false;
                        try {
                            ((LifecycleAwareStrategy<T>) backpressureStrategy)
                                .onBackpressureResolved(backpressureProvider);
                            if (debugMode) {
                                logger.debug("Backpressure resolved: level={}, threshold={}", level, threshold);
                            }
                        } catch (Exception e) {
                            if (debugMode) {
                                logger.error("Error in onBackpressureResolved callback", e);
                            }
                            // Continue monitoring despite callback error
                        }
                    } else if (backpressureActive) {
                        // Active backpressure - periodic check
                        try {
                            ((LifecycleAwareStrategy<T>) backpressureStrategy)
                                .onBackpressureActive(backpressureProvider);
                        } catch (Exception e) {
                            if (debugMode) {
                                logger.error("Error in onBackpressureActive callback", e);
                            }
                            // Continue monitoring despite callback error
                        }
                    }
                } catch (Exception e) {
                    // Catch all exceptions to prevent monitoring thread from dying
                    if (debugMode) {
                        logger.error("Error in backpressure monitoring", e);
                    }
                    // Continue monitoring despite errors
                }
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
        
        return monitor;
    }
    
    /**
     * Gets the backpressure threshold from the strategy.
     * 
     * <p>Uses the strategy's {@link BackpressureStrategy#getThreshold()} method
     * if available, otherwise returns a default value.
     * 
     * @return the threshold (0.0 to 1.0), or 0.7 as default
     */
    private double getBackpressureThreshold() {
        if (backpressureStrategy != null) {
            double threshold = backpressureStrategy.getThreshold();
            if (!Double.isNaN(threshold) && threshold >= 0.0 && threshold <= 1.0) {
                return threshold;
            }
        }
        // Default threshold if strategy doesn't provide one
        return 0.7;
    }
    
    @Override
    public void close() {
        closed = true;
        
        // Shutdown backpressure monitoring
        if (backpressureMonitor != null) {
            backpressureMonitor.shutdown();
            try {
                if (!backpressureMonitor.awaitTermination(1, TimeUnit.SECONDS)) {
                    backpressureMonitor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                backpressureMonitor.shutdownNow();
            }
        }
        
        retryManager.clearAll();
        
        // Wait for batch processor to finish processing queue (with timeout)
        // NOTE: This is a best-effort wait. Items submitted after close() is called
        // will be rejected, but items already in the queue will be processed.
        long deadline = System.currentTimeMillis() + CLOSE_QUEUE_WAIT_TIMEOUT_MS;
        long pollIntervalNanos = TimeUnit.MILLISECONDS.toNanos(CLOSE_POLL_INTERVAL_MS);
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            LockSupport.parkNanos(pollIntervalNanos);
            if (Thread.currentThread().isInterrupted()) {
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
        
        // Wait for all in-flight batches to complete (if concurrent limiting is enabled)
        if (dispatchSemaphore != null && activeBatchCount != null) {
            long batchWaitDeadline = System.currentTimeMillis() + (EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS * 1000);
            while (activeBatchCount.get() > 0 && System.currentTimeMillis() < batchWaitDeadline) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // Process any remaining items synchronously after executor is done
        List<PendingRequest<T>> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            // Create data list with pre-sized ArrayList (optimization: avoid stream overhead)
            List<T> dataList = new ArrayList<>(remaining.size());
            for (PendingRequest<T> req : remaining) {
                dataList.add(req.getData());
            }
            
            try {
                BatchResult<T> result = backend.dispatch(dataList);
                resultProcessor.processResults(remaining, result);
            } catch (Exception e) {
                resultProcessor.processFailure(remaining, e);
            }
        }
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
        if (closed) {
            // If already closed, just wait for in-flight batches
            return awaitInFlightBatches(timeout, unit);
        }
        
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        
        // Wait for queue to drain
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for queue to drain");
            }
        }
        
        // Wait for in-flight batches
        return awaitInFlightBatches(
            deadline - System.currentTimeMillis(), 
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Waits for all in-flight batches to complete.
     * 
     * @param timeoutMs the maximum time to wait in milliseconds
     * @param unit the time unit of the timeout
     * @return true if all batches completed within the timeout, false otherwise
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    private boolean awaitInFlightBatches(long timeout, TimeUnit unit) throws InterruptedException {
        if (activeBatchCount == null) {
            // No concurrent limiting - just wait for executor to finish
            if (executor.isShutdown()) {
                return executor.awaitTermination(timeout, unit);
            }
            // Executor not shut down yet - can't reliably wait
            return true;
        }
        
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (activeBatchCount.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for in-flight batches");
            }
        }
        
        return activeBatchCount.get() == 0;
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
     * // Adaptive batch sizing
     * if (metrics.getFailureRate() > 0.1) {
     *     batcher.updateBatchSize(5); // Reduce batch size
     * }
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
            throw new IllegalStateException(
                String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                    queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
            );
        }
        if (newBatchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        
        if (debugMode) {
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
            throw new IllegalStateException(
                String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
                    queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
            );
        }
        if (newLingerTime == null || newLingerTime.isNegative()) {
            throw new IllegalArgumentException("Linger time must be non-negative");
        }
        
        if (debugMode) {
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
        return new BatcherDiagnostics() {
            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public int getCurrentBatchSize() {
                return currentBatchSize;
            }

            @Override
            public Duration getCurrentLingerTime() {
                return currentLingerTime;
            }

            @Override
            public int getQueueDepth() {
                return queue.size();
            }
        };
    }
    
    /**
     * Gets the configuration used by this batcher.
     * 
     * <p>This method provides read-only access to the batcher's configuration.
     * The returned config reflects the initial configuration and does not
     * include dynamic updates (e.g., batch size changes via {@link #updateBatchSize(int)}).
     * 
     * @return the batcher configuration
     * @since 0.0.5
     */
    public BatcherConfig getConfig() {
        return config;
    }
}
