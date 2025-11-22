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
    
    private BatcherConfig(Builder builder) {
        this.batchSize = builder.batchSize;
        this.lingerTime = builder.lingerTime;
        this.atomicCommit = builder.atomicCommit;
        this.maxConcurrency = builder.maxConcurrency;
        this.autoReplaySuccesses = builder.autoReplaySuccesses;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public Duration getLingerTime() {
        return lingerTime;
    }
    
    public boolean isAtomicCommit() {
        return atomicCommit;
    }
    
    public int getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public boolean isAutoReplaySuccesses() {
        return autoReplaySuccesses;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int batchSize = 10;
        private Duration lingerTime = Duration.ofMillis(100);
        private boolean atomicCommit = false;
        private int maxConcurrency = 10;
        private boolean autoReplaySuccesses = false;
        
        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("Batch size must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder lingerTime(Duration lingerTime) {
            if (lingerTime == null || lingerTime.isNegative()) {
                throw new IllegalArgumentException("Linger time must be non-negative");
            }
            this.lingerTime = lingerTime;
            return this;
        }
        
        public Builder atomicCommit(boolean atomicCommit) {
            this.atomicCommit = atomicCommit;
            return this;
        }
        
        public Builder maxConcurrency(int maxConcurrency) {
            if (maxConcurrency <= 0) {
                throw new IllegalArgumentException("Max concurrency must be positive");
            }
            this.maxConcurrency = maxConcurrency;
            return this;
        }
        
        public Builder autoReplaySuccesses(boolean autoReplaySuccesses) {
            this.autoReplaySuccesses = autoReplaySuccesses;
            return this;
        }
        
        public BatcherConfig build() {
            return new BatcherConfig(this);
        }
    }
}

