package com.vajrapulse.vortex.backpressure;

/**
 * Rejects items with failure callback when backpressure exceeds threshold.
 * 
 * <p>Useful when:
 * <ul>
 *   <li>Caller needs to know item was rejected</li>
 *   <li>Caller can retry or handle rejection</li>
 *   <li>Rejection is better than silent dropping</li>
 * </ul>
 * 
 * <p>Items are rejected with a {@link BackpressureException} that includes
 * details about the backpressure level, threshold, and source.
 * 
 * <p>Example usage:
 * <pre>{@code
 * BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
 * }</pre>
 * 
 * @param <T> the type of items being handled
 */
public class RejectStrategy<T> implements BackpressureStrategy<T> {
    private final double threshold;
    
    /**
     * Creates a new reject strategy with the specified threshold.
     * 
     * @param threshold the backpressure threshold (0.0 to 1.0). When backpressure
     *                  level >= threshold, items will be rejected.
     * @throws IllegalArgumentException if threshold is not in valid range
     */
    public RejectStrategy(double threshold) {
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
            Exception reason = new BackpressureException(
                String.format(
                    "Backpressure too high: %.2f (threshold: %.2f, source: %s)",
                    context.backpressureLevel(),
                    threshold,
                    context.provider().getSourceName()
                ),
                context.backpressureLevel(),
                threshold,
                context.provider().getSourceName()
            );
            return BackpressureResult.reject(context.item(), reason);
        }
        return BackpressureResult.accept(context.item());
    }
    
    @Override
    public double getThreshold() {
        return threshold;
    }
}

