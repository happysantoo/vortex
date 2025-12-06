package com.vajrapulse.vortex;

import com.vajrapulse.vortex.backpressure.BackpressureProvider;
import com.vajrapulse.vortex.backpressure.BackpressureStrategy;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Configuration for the micro-batcher.
 */
public class BatcherConfig {
    private final int batchSize;
    private final Duration lingerTime;
    private final boolean atomicCommit;
    private final boolean autoReplaySuccesses;
    private final boolean perItemMetrics;
    private final boolean debugMode;
    private final int maxRetries;
    private final Duration retryDelay;
    private final Predicate<Throwable> retryableErrorPredicate;
    private final int maxQueueSize;
    private final BatchTracingHook tracingHook;
    private final BackpressureProvider backpressureProvider;
    private final BackpressureStrategy<?> backpressureStrategy;
    private final Duration backpressureMonitorInterval;
    private final Duration backpressureCacheTtl;
    private final int maxConcurrentBatches;
    
    /**
     * Private constructor for BatcherConfig.
     * 
     * @param builder the builder instance
     */
    private BatcherConfig(Builder builder) {
        this.batchSize = builder.batchSize;
        this.lingerTime = builder.lingerTime;
        this.atomicCommit = builder.atomicCommit;
        this.autoReplaySuccesses = builder.autoReplaySuccesses;
        this.perItemMetrics = builder.perItemMetrics;
        this.debugMode = builder.debugMode;
        this.maxRetries = builder.maxRetries;
        this.retryDelay = builder.retryDelay;
        this.retryableErrorPredicate = builder.retryableErrorPredicate;
        // Default to 2x batch size if not explicitly set
        this.maxQueueSize = builder.maxQueueSize != null ? builder.maxQueueSize : builder.batchSize * 2;
        this.tracingHook = builder.tracingHook;
        this.backpressureProvider = builder.backpressureProvider;
        this.backpressureStrategy = builder.backpressureStrategy;
        // Default to 100ms if not explicitly set
        this.backpressureMonitorInterval = builder.backpressureMonitorInterval != null 
            ? builder.backpressureMonitorInterval 
            : Duration.ofMillis(100);
        // Default to 50ms if not explicitly set (cache for 50ms to reduce provider calls)
        this.backpressureCacheTtl = builder.backpressureCacheTtl != null 
            ? builder.backpressureCacheTtl 
            : Duration.ofMillis(50);
        // Default to 0 (unlimited) if not explicitly set
        this.maxConcurrentBatches = builder.maxConcurrentBatches != null 
            ? builder.maxConcurrentBatches 
            : 0;
    }
    
    /**
     * Gets the batch size.
     * 
     * @return the batch size
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * Gets the linger time.
     * 
     * @return the linger time duration
     */
    public Duration getLingerTime() {
        return lingerTime;
    }
    
    /**
     * Checks if atomic commit mode is enabled.
     * 
     * @return true if atomic commit is enabled, false otherwise
     */
    public boolean isAtomicCommit() {
        return atomicCommit;
    }
    
    /**
     * Checks if auto-replay of successful items is enabled.
     * 
     * @return true if auto-replay is enabled, false otherwise
     */
    public boolean isAutoReplaySuccesses() {
        return autoReplaySuccesses;
    }
    
    /**
     * Checks if per-item metrics tracking is enabled.
     * 
     * @return true if per-item metrics are enabled, false otherwise
     */
    public boolean isPerItemMetrics() {
        return perItemMetrics;
    }
    
    /**
     * Checks if debug mode is enabled.
     * 
     * @return true if debug mode is enabled, false otherwise
     */
    public boolean isDebugMode() {
        return debugMode;
    }
    
    /**
     * Gets the maximum number of retries for failed items.
     * 
     * @return the maximum number of retries
     */
    public int getMaxRetries() {
        return maxRetries;
    }
    
    /**
     * Gets the retry delay between retry attempts.
     * 
     * @return the retry delay duration
     */
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    /**
     * Gets the predicate to determine if an error is retryable.
     * 
     * @return the retryable error predicate
     */
    public Predicate<Throwable> getRetryableErrorPredicate() {
        return retryableErrorPredicate;
    }
    
    /**
     * Gets the maximum queue size.
     * 
     * @return the maximum queue size
     */
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    /**
     * Gets the optional tracing hook.
     *
     * <p>When configured, the tracing hook receives notifications about key
     * lifecycle events (submissions, batch dispatch, retries). This can be used
     * to integrate with tracing or observability systems without adding
     * additional dependencies to the core library.
     *
     * @return the tracing hook, or {@code null} if not configured
     * @since 0.0.3
     */
    public BatchTracingHook getTracingHook() {
        return tracingHook;
    }
    
    /**
     * Gets the optional backpressure provider.
     *
     * <p>When configured along with a backpressure strategy, the MicroBatcher
     * will check backpressure before accepting items. This allows the system
     * to respond to pressure from various sources (queue depth, connection pools, etc.).
     *
     * @return the backpressure provider, or {@code null} if not configured
     * @since 0.0.4
     */
    public BackpressureProvider getBackpressureProvider() {
        return backpressureProvider;
    }
    
    /**
     * Gets the optional backpressure strategy.
     *
     * <p>When configured along with a backpressure provider, the MicroBatcher
     * will use this strategy to handle items when backpressure is detected.
     * Strategies can accept, reject, or drop items based on backpressure level.
     *
     * @param <T> the type of items being processed
     * @return the backpressure strategy, or {@code null} if not configured
     * @since 0.0.4
     */
    @SuppressWarnings("unchecked")
    public <T> BackpressureStrategy<T> getBackpressureStrategy() {
        return (BackpressureStrategy<T>) backpressureStrategy;
    }
    
    /**
     * Gets the backpressure monitoring interval.
     *
     * <p>This is the interval at which the MicroBatcher checks for backpressure
     * state transitions when using a {@link com.vajrapulse.vortex.backpressure.LifecycleAwareStrategy}.
     * The default is 100ms.
     *
     * @return the monitoring interval duration
     * @since 0.0.4
     */
    public Duration getBackpressureMonitorInterval() {
        return backpressureMonitorInterval;
    }
    
    /**
     * Gets the backpressure level cache TTL.
     *
     * <p>This is the time-to-live for cached backpressure levels. When a backpressure
     * level is fetched from the provider, it is cached for this duration to reduce
     * the overhead of calling the provider on every submission.
     *
     * <p>The default is 50ms, which provides a good balance between reducing provider
     * calls and maintaining responsiveness to backpressure changes.
     *
     * @return the cache TTL duration
     * @since 0.0.5
     */
    public Duration getBackpressureCacheTtl() {
        return backpressureCacheTtl;
    }
    
    /**
     * Gets the maximum number of batches that can be dispatched concurrently.
     *
     * <p>This prevents overwhelming the connection pool by limiting concurrent
     * batch dispatches. When set to 0 (default), there is no limit.
     *
     * <p>Recommended value: 80% of connection pool size. For example, for a
     * 10-connection pool, set to 8 to leave 2 connections available for other
     * operations.
     *
     * @return the maximum concurrent batches (0 means unlimited)
     * @since 0.0.7
     */
    public int getMaxConcurrentBatches() {
        return maxConcurrentBatches;
    }
    
    /**
     * Creates a new builder instance.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for BatcherConfig.
     * 
     * <p>Provides a fluent API for constructing BatcherConfig instances.
     * All configuration options have sensible defaults.
     */
    public static class Builder {
        /**
         * Creates a new Builder instance with default values.
         */
        public Builder() {
            // Default constructor - all fields initialized with defaults
        }
        
        private int batchSize = 10;
        private Duration lingerTime = Duration.ofMillis(100);
        private boolean atomicCommit = false;
        private boolean autoReplaySuccesses = false;
        private boolean perItemMetrics = false;
        private boolean debugMode = false;
        private int maxRetries = 0;
        private Duration retryDelay = Duration.ZERO;
        private Predicate<Throwable> retryableErrorPredicate = t -> false;
        private Integer maxQueueSize = null; // null means use default (2x batchSize)
        private BatchTracingHook tracingHook = null;
        private BackpressureProvider backpressureProvider = null;
        private BackpressureStrategy<?> backpressureStrategy = null;
        private Duration backpressureMonitorInterval = null; // null means use default (100ms)
        private Duration backpressureCacheTtl = null; // null means use default (50ms)
        private Integer maxConcurrentBatches = null; // null means use default (0 = unlimited)
        
        /**
         * Sets the batch size.
         * 
         * @param batchSize the batch size (must be positive)
         * @return this builder instance
         * @throws IllegalArgumentException if batchSize is not positive
         */
        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }
        
        /**
         * Sets the linger time.
         * 
         * @param lingerTime the linger time duration (must be non-negative)
         * @return this builder instance
         * @throws IllegalArgumentException if lingerTime is null or negative
         */
        public Builder lingerTime(Duration lingerTime) {
            if (lingerTime == null || lingerTime.isNegative()) {
                throw new IllegalArgumentException("Linger time must be non-negative");
            }
            this.lingerTime = lingerTime;
            return this;
        }
        
        /**
         * Sets atomic commit mode.
         * 
         * @param atomicCommit true to enable atomic commit mode
         * @return this builder instance
         */
        public Builder atomicCommit(boolean atomicCommit) {
            this.atomicCommit = atomicCommit;
            return this;
        }
        
        /**
         * Sets auto-replay of successful items.
         * 
         * @param autoReplaySuccesses true to enable auto-replay
         * @return this builder instance
         */
        public Builder autoReplaySuccesses(boolean autoReplaySuccesses) {
            this.autoReplaySuccesses = autoReplaySuccesses;
            return this;
        }
        
        /**
         * Enables per-item metrics tracking.
         * When enabled, metrics are recorded for each individual item:
         * - vortex.item.submit.latency - Time from submit to batch completion
         * - vortex.item.wait.time - Time item waits in queue
         * - vortex.item.batch.size - Size of batch when item was processed
         * 
         * @param perItemMetrics true to enable per-item metrics
         * @return this builder instance
         */
        public Builder perItemMetrics(boolean perItemMetrics) {
            this.perItemMetrics = perItemMetrics;
            return this;
        }
        
        /**
         * Enables debug mode with detailed logging.
         * When enabled, logs detailed information about:
         * - Batch formation events
         * - Item submission events
         * - Batch dispatch events
         * - Queue depth changes
         * - Timing information
         * 
         * @param debugMode true to enable debug mode
         * @return this builder instance
         */
        public Builder debugMode(boolean debugMode) {
            this.debugMode = debugMode;
            return this;
        }
        
        /**
         * Sets the maximum number of retries for failed items.
         * 
         * @param maxRetries the maximum number of retries (must be non-negative)
         * @return this builder instance
         * @throws IllegalArgumentException if maxRetries is negative
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Max retries must be non-negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }
        
        /**
         * Sets the retry delay between retry attempts.
         * 
         * @param retryDelay the retry delay duration (must be non-negative)
         * @return this builder instance
         * @throws IllegalArgumentException if retryDelay is null or negative
         */
        public Builder retryDelay(Duration retryDelay) {
            if (retryDelay == null || retryDelay.isNegative()) {
                throw new IllegalArgumentException("Retry delay must be non-negative");
            }
            this.retryDelay = retryDelay;
            return this;
        }
        
        /**
         * Sets the predicate to determine if an error is retryable.
         * Only errors that match this predicate will be retried.
         * 
         * @param retryableErrorPredicate the predicate to test errors (must not be null)
         * @return this builder instance
         * @throws IllegalArgumentException if retryableErrorPredicate is null
         */
        public Builder retryableErrorPredicate(Predicate<Throwable> retryableErrorPredicate) {
            if (retryableErrorPredicate == null) {
                throw new IllegalArgumentException("Retryable error predicate must not be null");
            }
            this.retryableErrorPredicate = retryableErrorPredicate;
            return this;
        }
        
        /**
         * Sets the maximum queue size for pending requests.
         * When the queue is full, new submissions will be rejected with RejectedExecutionException
         * after waiting up to 100ms.
         * 
         * <p>If not set, defaults to 2x the batch size to allow buffering of at least 2 batches.
         * 
         * <p>The queue size should be at least equal to the batch size to allow at least one
         * full batch to be queued. Setting it too small may cause frequent rejections.
         * 
         * @param maxQueueSize the maximum queue size (must be at least batchSize)
         * @return this builder instance
         * @throws IllegalArgumentException if maxQueueSize is less than batchSize
         */
        public Builder maxQueueSize(int maxQueueSize) {
            if (maxQueueSize < batchSize) {
                throw new IllegalArgumentException(
                    "Max queue size (" + maxQueueSize + ") must be at least equal to batch size (" + batchSize + ")");
            }
            this.maxQueueSize = maxQueueSize;
            return this;
        }
        
        /**
         * Sets an optional tracing hook for observability.
         *
         * <p>The tracing hook is notified of key lifecycle events such as:
         * <ul>
         *   <li>Item submissions</li>
         *   <li>Batch dispatch start/success/failure</li>
         *   <li>Scheduled retries</li>
         * </ul>
         *
         * <p>This allows applications to integrate Vortex with tracing systems
         * such as OpenTelemetry without introducing additional dependencies in
         * the core library.
         *
         * @param tracingHook the tracing hook implementation (may be null)
         * @return this builder instance
         */
        public Builder tracingHook(BatchTracingHook tracingHook) {
            this.tracingHook = tracingHook;
            return this;
        }
        
        /**
         * Sets an optional backpressure provider.
         *
         * <p>When configured along with a backpressure strategy, the MicroBatcher
         * will check backpressure before accepting items. The provider reports
         * backpressure level (0.0 to 1.0) from various sources such as:
         * <ul>
         *   <li>Queue depth</li>
         *   <li>Connection pool utilization</li>
         *   <li>Memory pressure</li>
         *   <li>CPU utilization</li>
         * </ul>
         *
         * <p>Example:
         * <pre>{@code
         * BackpressureProvider provider = new QueueDepthBackpressureProvider(
         *     () -> batcher.diagnostics().getQueueDepth(),
         *     1000
         * );
         * config.backpressureProvider(provider);
         * }</pre>
         *
         * @param backpressureProvider the backpressure provider (may be null)
         * @return this builder instance
         * @since 0.0.4
         */
        public Builder backpressureProvider(BackpressureProvider backpressureProvider) {
            this.backpressureProvider = backpressureProvider;
            return this;
        }
        
        /**
         * Sets an optional backpressure strategy.
         *
         * <p>When configured along with a backpressure provider, the MicroBatcher
         * will use this strategy to handle items when backpressure is detected.
         * Strategies can:
         * <ul>
         *   <li>Accept items (proceed normally)</li>
         *   <li>Reject items (return failure callback)</li>
         *   <li>Drop items (silently ignore)</li>
         *   <li>Overflow items (store for later replay)</li>
         * </ul>
         *
         * <p>Example:
         * <pre>{@code
         * BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
         * config.backpressureStrategy(strategy);
         * }</pre>
         *
         * @param <T> the type of items being processed
         * @param backpressureStrategy the backpressure strategy (may be null)
         * @return this builder instance
         * @since 0.0.4
         */
        public <T> Builder backpressureStrategy(BackpressureStrategy<T> backpressureStrategy) {
            @SuppressWarnings("unchecked")
            BackpressureStrategy<?> cast = (BackpressureStrategy<?>) backpressureStrategy;
            this.backpressureStrategy = cast;
            return this;
        }
        
        /**
         * Sets the backpressure monitoring interval.
         *
         * <p>This is the interval at which the MicroBatcher checks for backpressure
         * state transitions when using a {@link com.vajrapulse.vortex.backpressure.LifecycleAwareStrategy}.
         * The default is 100ms.
         *
         * <p>Shorter intervals provide faster response to backpressure changes but
         * consume more CPU. Longer intervals reduce CPU usage but may delay detection
         * of backpressure resolution.
         *
         * <p>Recommended values:
         * <ul>
         *   <li>50-100ms: For high-throughput systems requiring fast response</li>
         *   <li>100-200ms: Default, suitable for most use cases</li>
         *   <li>200-500ms: For low-throughput systems or when CPU is a concern</li>
         * </ul>
         *
         * <p>Example:
         * <pre>{@code
         * config.backpressureMonitorInterval(Duration.ofMillis(50));
         * }</pre>
         *
         * @param interval the monitoring interval (must be positive)
         * @return this builder instance
         * @throws IllegalArgumentException if interval is null, zero, or negative
         * @since 0.0.4
         */
        public Builder backpressureMonitorInterval(Duration interval) {
            if (interval == null || interval.isZero() || interval.isNegative()) {
                throw new IllegalArgumentException(
                    "Backpressure monitor interval must be positive, got: " + interval
                );
            }
            this.backpressureMonitorInterval = interval;
            return this;
        }
        
        /**
         * Sets the backpressure level cache TTL.
         *
         * <p>This is the time-to-live for cached backpressure levels. When a backpressure
         * level is fetched from the provider, it is cached for this duration to reduce
         * the overhead of calling the provider on every submission.
         *
         * <p>The default is 50ms, which provides a good balance between reducing provider
         * calls and maintaining responsiveness to backpressure changes.
         *
         * <p>Recommended values:
         * <ul>
         *   <li>10-50ms: For high-throughput systems with fast-changing backpressure</li>
         *   <li>50-100ms: Default, suitable for most use cases</li>
         *   <li>100-200ms: For low-throughput systems or when provider calls are expensive</li>
         * </ul>
         *
         * <p>Example:
         * <pre>{@code
         * config.backpressureCacheTtl(Duration.ofMillis(100));
         * }</pre>
         *
         * @param ttl the cache TTL (must be positive)
         * @return this builder instance
         * @throws IllegalArgumentException if ttl is null, zero, or negative
         * @since 0.0.5
         */
        public Builder backpressureCacheTtl(Duration ttl) {
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException(
                    "Backpressure cache TTL must be positive, got: " + ttl
                );
            }
            this.backpressureCacheTtl = ttl;
            return this;
        }
        
        /**
         * Sets the maximum number of batches that can be dispatched concurrently.
         *
         * <p>This prevents overwhelming the connection pool by limiting concurrent
         * batch dispatches. Recommended value: 80% of connection pool size.
         *
         * <p>Example: For a 10-connection pool, set to 8 to leave 2 connections
         * available for other operations.
         *
         * <p>Default: 0 (unlimited - no limit)
         *
         * <p>When a batch cannot be dispatched due to the limit, it will be rejected
         * and the items in the batch will be notified via their callbacks (if using
         * {@link com.vajrapulse.vortex.MicroBatcher#submitWithCallback(Object, java.util.function.BiConsumer)})
         * or their futures will complete exceptionally.
         *
         * <p>Example:
         * <pre>{@code
         * // Limit to 80% of 10-connection pool
         * config.maxConcurrentBatches(8);
         * }</pre>
         *
         * @param maxConcurrentBatches the maximum concurrent batches (must be >= 0, where 0 means unlimited)
         * @return this builder instance
         * @throws IllegalArgumentException if maxConcurrentBatches is negative
         * @since 0.0.7
         */
        public Builder maxConcurrentBatches(int maxConcurrentBatches) {
            if (maxConcurrentBatches < 0) {
                throw new IllegalArgumentException("maxConcurrentBatches must be >= 0 (0 means unlimited)");
            }
            this.maxConcurrentBatches = maxConcurrentBatches;
            return this;
        }
        
        /**
         * Builds the BatcherConfig instance.
         * 
         * @return a new BatcherConfig instance
         */
        public BatcherConfig build() {
            return new BatcherConfig(this);
        }
    }
}

