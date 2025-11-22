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
         * Builds the BatcherConfig instance.
         * 
         * @return a new BatcherConfig instance
         */
        public BatcherConfig build() {
            return new BatcherConfig(this);
        }
    }
}

