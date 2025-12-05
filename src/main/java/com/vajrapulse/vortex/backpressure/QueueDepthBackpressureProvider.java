package com.vajrapulse.vortex.backpressure;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Backpressure provider that monitors the MicroBatcher's internal queue depth.
 * 
 * <p>This provider calculates backpressure based on queue utilization:
 * <ul>
 *   <li><strong>0.0</strong>: Queue is empty (no backpressure)</li>
 *   <li><strong>0.5</strong>: Queue is 50% full (moderate backpressure)</li>
 *   <li><strong>1.0</strong>: Queue is full (severe backpressure)</li>
 * </ul>
 * 
 * <p><strong>Backpressure Calculation:</strong>
 * <pre>{@code
 * backpressure = queueDepth / maxQueueSize
 * }</pre>
 * 
 * <p><strong>Usage with AdaptiveLoadPattern (VajraPulse):</strong>
 * <pre>{@code
 * // Create queue depth supplier
 * Supplier<Integer> queueDepthSupplier = () -> batcher.getQueueDepth();
 * 
 * // Create backpressure provider
 * BackpressureProvider backpressureProvider = new QueueDepthBackpressureProvider(
 *     queueDepthSupplier,
 *     maxQueueSize  // e.g., 1000 items (20 batches × 50 items)
 * );
 * 
 * // Use in AdaptiveLoadPattern
 * AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
 *     initialTps,
 *     rampIncrement,
 *     rampDecrement,
 *     rampInterval,
 *     maxTps,
 *     sustainDuration,
 *     errorThreshold,
 *     metricsProvider,
 *     backpressureProvider  // Queue-only backpressure
 * );
 * }</pre>
 * 
 * <p><strong>Relationship to RejectStrategy:</strong>
 * <ul>
 *   <li><strong>QueueDepthBackpressureProvider</strong>: Used by AdaptiveLoadPattern
 *       to adjust TPS gradually (every 5 seconds). Provides smooth adaptation.</li>
 *   <li><strong>RejectStrategy</strong>: Used by MicroBatcher to reject items
 *       immediately when backpressure >= threshold (e.g., 0.7). Prevents queue overflow.</li>
 *   <li>Both use the same backpressure signal, but for different purposes:
 *       <ul>
 *         <li>AdaptiveLoadPattern: Gradual TPS adjustment (load pattern level)</li>
 *         <li>RejectStrategy: Immediate rejection (item submission level)</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><strong>Recommended Configuration:</strong>
 * <ul>
 *   <li><strong>Max Queue Size</strong>: 20-50 batches worth of items
 *       <ul>
 *         <li>Example: 20 batches × 50 items/batch = 1000 items</li>
 *         <li>Larger queue = more buffering, but more memory</li>
 *         <li>Smaller queue = less memory, but more rejections</li>
 *       </ul>
 *   </li>
 *   <li><strong>RejectStrategy Threshold</strong>: 0.7 (70% capacity)
 *       <ul>
 *         <li>Rejects items when queue > 70% full</li>
 *         <li>Prevents queue from filling completely</li>
 *         <li>Leaves 30% headroom for burst traffic</li>
 *       </ul>
 *   </li>
 *   <li><strong>AdaptiveLoadPattern Threshold</strong>: 0.7 (70% capacity)
 *       <ul>
 *         <li>Ramps down TPS when backpressure >= 0.7</li>
 *         <li>Should match RejectStrategy threshold for consistency</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p><strong>Why Queue-Only Backpressure?</strong>
 * <ul>
 *   <li>Queue depth directly measures "can the system keep up?"</li>
 *   <li>If queue is full, system can't process items fast enough (regardless of root cause)</li>
 *   <li>Simpler than monitoring multiple signals (connection pool, network, etc.)</li>
 *   <li>Works with any backend (not just JDBC/databases)</li>
 * </ul>
 * 
 * <p>Linear scaling is simple and effective for most use cases. For early pressure
 * detection, consider using a composite provider with multiple sources or implementing
 * a custom provider with logarithmic scaling.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BackpressureProvider provider = new QueueDepthBackpressureProvider(
 *     () -> batcher.getQueueDepth(),
 *     1000  // max capacity
 * );
 * }</pre>
 * 
 * @since 0.0.4
 * @see com.vajrapulse.vortex.MicroBatcher#getQueueDepth()
 */
public class QueueDepthBackpressureProvider implements BackpressureProvider {
    private final Supplier<Integer> queueDepthSupplier;
    private final int maxCapacity;
    
    /**
     * Creates a new queue depth backpressure provider.
     * 
     * @param queueDepthSupplier supplier that returns the current queue depth
     * @param maxCapacity the maximum queue capacity
     * @throws IllegalArgumentException if maxCapacity is not positive
     */
    public QueueDepthBackpressureProvider(Supplier<Integer> queueDepthSupplier, int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException("maxCapacity must be positive, got: " + maxCapacity);
        }
        if (queueDepthSupplier == null) {
            throw new IllegalArgumentException("queueDepthSupplier cannot be null");
        }
        this.queueDepthSupplier = queueDepthSupplier;
        this.maxCapacity = maxCapacity;
    }
    
    @Override
    public double getBackpressureLevel() {
        int queueDepth = queueDepthSupplier.get();
        
        if (queueDepth <= 0) {
            return 0.0;
        }
        
        if (queueDepth >= maxCapacity) {
            return 1.0;
        }
        
        // Simple linear scaling
        return (double) queueDepth / maxCapacity;
    }
    
    @Override
    public String getSourceName() {
        return "Vortex Queue Depth";
    }
    
    @Override
    public Map<String, Object> getDetails() {
        int queueDepth = queueDepthSupplier.get();
        double utilization = maxCapacity > 0 ? (double) queueDepth / maxCapacity * 100.0 : 0.0;
        return Map.of(
            "queueDepth", queueDepth,
            "maxCapacity", maxCapacity,
            "utilization", String.format("%.2f%%", utilization)
        );
    }
}

