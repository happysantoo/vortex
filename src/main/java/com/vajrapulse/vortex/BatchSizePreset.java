package com.vajrapulse.vortex;

import java.time.Duration;

/**
 * Predefined batch size and linger time configurations for common use cases.
 * 
 * <p>These presets provide optimized defaults for different workload patterns.
 * You can use them directly or as a starting point for custom configuration.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BatcherConfig config = BatchSizePreset.MEDIUM.toConfig();
 * MicroBatcher<String> batcher = new MicroBatcher<>(backend, config);
 * }</pre>
 * 
 * @since 0.0.5
 */
public enum BatchSizePreset {
    /**
     * Tiny batches for ultra-low latency scenarios.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Latency is critical (&lt; 50ms)</li>
     *   <li>Throughput is less important</li>
     *   <li>Items are processed quickly</li>
     * </ul>
     */
    TINY(5, Duration.ofMillis(10)),
    
    /**
     * Small batches for low-latency scenarios.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Latency is important (&lt; 100ms)</li>
     *   <li>Moderate throughput is acceptable</li>
     *   <li>Real-time or near-real-time processing</li>
     * </ul>
     */
    SMALL(10, Duration.ofMillis(50)),
    
    /**
     * Medium batches for balanced scenarios (default).
     * 
     * <p>Use when:
     * <ul>
     *   <li>Balancing latency and throughput</li>
     *   <li>General-purpose batching</li>
     *   <li>Most common use case</li>
     * </ul>
     */
    MEDIUM(20, Duration.ofMillis(100)),
    
    /**
     * Large batches for high-throughput scenarios.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Throughput is critical</li>
     *   <li>Latency up to 500ms is acceptable</li>
     *   <li>Processing large volumes efficiently</li>
     * </ul>
     */
    LARGE(50, Duration.ofMillis(200)),
    
    /**
     * Huge batches for maximum throughput scenarios.
     * 
     * <p>Use when:
     * <ul>
     *   <li>Maximum throughput is required</li>
     *   <li>Latency up to 1 second is acceptable</li>
     *   <li>Batch processing workloads</li>
     * </ul>
     */
    HUGE(100, Duration.ofMillis(500));
    
    private final int batchSize;
    private final Duration lingerTime;
    
    BatchSizePreset(int batchSize, Duration lingerTime) {
        this.batchSize = batchSize;
        this.lingerTime = lingerTime;
    }
    
    /**
     * Gets the batch size for this preset.
     * 
     * @return the batch size
     */
    public int getBatchSize() {
        return batchSize;
    }
    
    /**
     * Gets the linger time for this preset.
     * 
     * @return the linger time duration
     */
    public Duration getLingerTime() {
        return lingerTime;
    }
    
    /**
     * Creates a BatcherConfig with this preset's values.
     * 
     * <p>The config uses sensible defaults for other settings:
     * <ul>
     *   <li>maxQueueSize: 2 × batchSize</li>
     *   <li>atomicCommit: false</li>
     *   <li>autoReplaySuccesses: false</li>
     *   <li>perItemMetrics: false</li>
     *   <li>debugMode: false</li>
     *   <li>maxRetries: 0</li>
     * </ul>
     * 
     * <p>You can customize further using the builder:
     * <pre>{@code
     * BatcherConfig config = BatchSizePreset.MEDIUM.toConfig()
     *     .toBuilder()
     *     .maxRetries(3)
     *     .build();
     * }</pre>
     * 
     * @return a new BatcherConfig with this preset's values
     */
    public BatcherConfig toConfig() {
        return BatcherConfig.builder()
            .batchSize(batchSize)
            .lingerTime(lingerTime)
            .maxQueueSize(batchSize * 2)  // Default: 2x batch size
            .build();
    }
    
    /**
     * Creates a BatcherConfig builder pre-configured with this preset's values.
     * 
     * <p>This allows you to start with preset values and customize further:
     * <pre>{@code
     * BatcherConfig config = BatchSizePreset.MEDIUM.toConfigBuilder()
     *     .maxRetries(3)
     *     .retryDelay(Duration.ofMillis(100))
     *     .build();
     * }</pre>
     * 
     * @return a BatcherConfig.Builder pre-configured with this preset's values
     */
    public BatcherConfig.Builder toConfigBuilder() {
        return BatcherConfig.builder()
            .batchSize(batchSize)
            .lingerTime(lingerTime)
            .maxQueueSize(batchSize * 2);
    }
}

