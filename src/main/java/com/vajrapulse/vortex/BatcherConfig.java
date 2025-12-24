package com.vajrapulse.vortex;

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
    private final double queueRejectionThreshold;
    private final BatchTracingHook tracingHook;
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
        // Default to 1.0 (100% full) if not explicitly set
        this.queueRejectionThreshold = builder.queueRejectionThreshold != null 
            ? builder.queueRejectionThreshold 
            : 1.0;
        this.tracingHook = builder.tracingHook;
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
     * Gets the queue rejection threshold as a fraction (0.0 to 1.0).
     * 
     * <p>Items will be rejected when the queue depth reaches this threshold
     * of the maximum queue size. For example:
     * <ul>
     *   <li>1.0 (default): Reject when queue is 100% full</li>
     *   <li>0.8: Reject when queue is 80% full</li>
     *   <li>0.5: Reject when queue is 50% full</li>
     * </ul>
     * 
     * <p>This provides proactive rejection by rejecting items before the queue
     * is completely full, giving the system time to process existing items.
     * 
     * @return the queue rejection threshold (0.0 to 1.0, default: 1.0)
     * @since 0.0.9
     */
    public double getQueueRejectionThreshold() {
        return queueRejectionThreshold;
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
     * Creates a configuration optimized for high-throughput scenarios.
     *
     * <p>Use with constructor: {@code new MicroBatcher<>(backend, BatcherConfig.highThroughputPreset(), registry)}
     *
     * @return a new {@link BatcherConfig} tuned for maximum throughput
     * @since 0.0.10
     */
    public static BatcherConfig highThroughputPreset() {
        return builder()
            .batchSize(100)
            .lingerTime(Duration.ofMillis(500))
            .maxQueueSize(500)
            .build();
    }

    /**
     * Creates a configuration optimized for low-latency scenarios.
     *
     * <p>Use with constructor: {@code new MicroBatcher<>(backend, BatcherConfig.lowLatencyPreset(), registry)}
     *
     * @return a new {@link BatcherConfig} tuned for minimal latency
     * @since 0.0.10
     */
    public static BatcherConfig lowLatencyPreset() {
        return builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(10))
            .maxQueueSize(20)
            .build();
    }

    /**
     * Creates a configuration optimized for balanced scenarios (default).
     *
     * <p>Use with constructor: {@code new MicroBatcher<>(backend, BatcherConfig.balancedPreset(), registry)}
     *
     * @return a new {@link BatcherConfig} tuned for balanced latency and throughput
     * @since 0.0.10
     */
    public static BatcherConfig balancedPreset() {
        return builder()
            .batchSize(20)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(50)
            .build();
    }

    /**
     * Creates a configuration optimized for resilient scenarios with retry support.
     *
     * <p>Use with constructor: {@code new MicroBatcher<>(backend, BatcherConfig.resilientPreset(predicate), registry)}
     *
     * @param retryableErrorPredicate predicate to determine which errors should be retried
     * @return a new {@link BatcherConfig} tuned for resilience
     * @since 0.0.10
     */
    public static BatcherConfig resilientPreset(Predicate<Throwable> retryableErrorPredicate) {
        return builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxRetries(3)
            .retryDelay(Duration.ofMillis(100))
            .retryableErrorPredicate(retryableErrorPredicate)
            .maxQueueSize(30)
            .build();
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
        private Double queueRejectionThreshold = null; // null means use default (1.0 = 100% full)
        private BatchTracingHook tracingHook = null;
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
         * When the queue reaches the rejection threshold (see {@link #queueRejectionThreshold(double)}),
         * new submissions will be rejected immediately.
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
         * Sets the queue rejection threshold as a fraction (0.0 to 1.0).
         * 
         * <p>Items will be rejected when the queue depth reaches this threshold of the maximum
         * queue size. For example:
         * <ul>
         *   <li>1.0 (default): Reject when queue is 100% full</li>
         *   <li>0.8: Reject when queue is 80% full</li>
         *   <li>0.5: Reject when queue is 50% full</li>
         * </ul>
         * 
         * <p>This provides proactive rejection by rejecting items before the queue is
         * completely full, giving the system time to process existing items. This is
         * particularly useful in high-throughput scenarios where you want to start rejecting
         * items early to prevent the queue from becoming completely saturated.
         * 
         * <p>Example:
         * <pre>{@code
         * // Reject items when queue is 80% full
         * config.queueRejectionThreshold(0.8);
         * 
         * // With maxQueueSize of 100, items will be rejected when queue depth >= 80
         * }</pre>
         * 
         * @param threshold the rejection threshold (0.0 to 1.0, where 1.0 = 100% full)
         * @return this builder instance
         * @throws IllegalArgumentException if threshold is not between 0.0 and 1.0
         * @since 0.0.9
         */
        public Builder queueRejectionThreshold(double threshold) {
            if (threshold < 0.0 || threshold > 1.0) {
                throw new IllegalArgumentException(
                    "Queue rejection threshold must be between 0.0 and 1.0, got: " + threshold
                );
            }
            this.queueRejectionThreshold = threshold;
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
         * {@link com.vajrapulse.vortex.MicroBatcher#submit(Object, ItemCallback)})
         * with a failure result.
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
            // Cross-field validation to ensure consistent configuration
            if (maxRetries == 0 && !retryDelay.isZero()) {
                throw new IllegalStateException("Retry delay is set but maxRetries is 0 – either enable retries or reset retryDelay");
            }
            if (maxRetries > 0 && retryableErrorPredicate == null) {
                throw new IllegalStateException("Retryable error predicate must be configured when maxRetries > 0");
            }
            if (maxQueueSize != null && maxQueueSize < batchSize) {
                throw new IllegalStateException(
                    "Max queue size (" + maxQueueSize + ") must be at least equal to batch size (" + batchSize + ")"
                );
            }
            return new BatcherConfig(this);
        }
    }
}

