package com.vajrapulse.vortex.backpressure;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Backpressure provider based on queue depth.
 * 
 * <p>Calculates backpressure based on the ratio of queued items to maximum queue capacity.
 * Uses simple linear scaling:
 * <ul>
 *   <li>queueDepth = 0: backpressure = 0.0</li>
 *   <li>queueDepth &lt; maxCapacity: backpressure = queueDepth / maxCapacity (linear)</li>
 *   <li>queueDepth &gt;= maxCapacity: backpressure = 1.0</li>
 * </ul>
 * 
 * <p>Linear scaling is simple and effective for most use cases. For early pressure
 * detection, consider using a composite provider with multiple sources or implementing
 * a custom provider with logarithmic scaling.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BackpressureProvider provider = new QueueDepthBackpressureProvider(
 *     () -> batcher.diagnostics().getQueueDepth(),
 *     1000  // max capacity
 * );
 * }</pre>
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

