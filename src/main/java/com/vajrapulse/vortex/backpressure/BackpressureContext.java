package com.vajrapulse.vortex.backpressure;

/**
 * Context for backpressure handling.
 * 
 * <p>Provides all information needed for a strategy to make a decision about
 * how to handle an item when backpressure is detected.
 * 
 * @param <T> the type of item being handled
 * @param item the item that triggered the backpressure check
 * @param backpressureLevel the current backpressure level (0.0 to 1.0)
 * @param provider the backpressure provider that reported the level
 */
public record BackpressureContext<T>(
    T item,
    double backpressureLevel,
    BackpressureProvider provider
) {
    /**
     * Creates a new backpressure context.
     * 
     * @param item the item being handled
     * @param backpressureLevel the current backpressure level (must be 0.0 to 1.0)
     * @param provider the backpressure provider (must not be null)
     * @throws IllegalArgumentException if provider is null or backpressureLevel is out of range
     */
    public BackpressureContext {
        if (provider == null) {
            throw new IllegalArgumentException("Provider cannot be null");
        }
        if (backpressureLevel < 0.0 || backpressureLevel > 1.0) {
            throw new IllegalArgumentException(
                "Backpressure level must be between 0.0 and 1.0, got: " + backpressureLevel
            );
        }
    }
}

