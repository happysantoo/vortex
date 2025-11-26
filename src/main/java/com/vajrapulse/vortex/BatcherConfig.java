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
     * Creates a new builder instance.
     * 
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for BatcherConfig.
     */
    public static class Builder {
        private int batchSize = 10;
        private Duration lingerTime = Duration.ofMillis(100);
        private boolean atomicCommit = false;
        private boolean autoReplaySuccesses = false;
        private boolean perItemMetrics = false;
        private boolean debugMode = false;
        private int maxRetries = 0;
        private Duration retryDelay = Duration.ZERO;
        private Predicate<Throwable> retryableErrorPredicate = t -> false;
        
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
         * Builds the BatcherConfig instance.
         * 
         * @return a new BatcherConfig instance
         */
        public BatcherConfig build() {
            return new BatcherConfig(this);
        }
    }
}

