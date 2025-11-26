package com.vajrapulse.vortex;

import java.time.Duration;

/**
 * Configuration for the micro-batcher.
 */
public class BatcherConfig {
    private final int batchSize;
    private final Duration lingerTime;
    private final boolean atomicCommit;
    private final int maxConcurrency;
    private final boolean autoReplaySuccesses;
    private final boolean perItemMetrics;
    private final boolean debugMode;
    
    /**
     * Private constructor for BatcherConfig.
     * 
     * @param builder the builder instance
     */
    private BatcherConfig(Builder builder) {
        this.batchSize = builder.batchSize;
        this.lingerTime = builder.lingerTime;
        this.atomicCommit = builder.atomicCommit;
        this.maxConcurrency = builder.maxConcurrency;
        this.autoReplaySuccesses = builder.autoReplaySuccesses;
        this.perItemMetrics = builder.perItemMetrics;
        this.debugMode = builder.debugMode;
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
     * Gets the maximum concurrency.
     * 
     * @return the maximum concurrency
     */
    public int getMaxConcurrency() {
        return maxConcurrency;
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
        private int maxConcurrency = 10;
        private boolean autoReplaySuccesses = false;
        private boolean perItemMetrics = false;
        private boolean debugMode = false;
        
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
         * Sets the maximum concurrency.
         * 
         * @param maxConcurrency the maximum concurrency (must be positive)
         * @return this builder instance
         * @throws IllegalArgumentException if maxConcurrency is not positive
         */
        public Builder maxConcurrency(int maxConcurrency) {
            if (maxConcurrency <= 0) {
                throw new IllegalArgumentException("Max concurrency must be positive");
            }
            this.maxConcurrency = maxConcurrency;
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
         * Builds the BatcherConfig instance.
         * 
         * @return a new BatcherConfig instance
         */
        public BatcherConfig build() {
            return new BatcherConfig(this);
        }
    }
}

