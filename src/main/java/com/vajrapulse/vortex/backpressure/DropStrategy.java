package com.vajrapulse.vortex.backpressure;

/**
 * Drops items silently when backpressure exceeds threshold.
 * 
 * <p>Useful when:
 * <ul>
 *   <li>Items are not critical (e.g., metrics, logs)</li>
 *   <li>Dropping is preferable to queuing (prevents memory growth)</li>
 *   <li>System should prioritize stability over completeness</li>
 * </ul>
 * 
 * <p>Items are dropped without calling failure callbacks. The caller receives
 * a successful result, but the item is not actually processed.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BackpressureStrategy<String> strategy = new DropStrategy<>(0.7);
 * }</pre>
 * 
 * @param <T> the type of items being handled
 */
public class DropStrategy<T> implements BackpressureStrategy<T> {
    private final double threshold;
    
    /**
     * Creates a new drop strategy with the specified threshold.
     * 
     * @param threshold the backpressure threshold (0.0 to 1.0). When backpressure
     *                  level >= threshold, items will be dropped.
     * @throws IllegalArgumentException if threshold is not in valid range
     */
    public DropStrategy(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                "Threshold must be between 0.0 and 1.0, got: " + threshold
            );
        }
        this.threshold = threshold;
    }
    
    @Override
    public BackpressureResult<T> handle(BackpressureContext<T> context) {
        if (context.backpressureLevel() >= threshold) {
            return BackpressureResult.drop(context.item());
        }
        return BackpressureResult.accept(context.item());
    }
}

