package com.vajrapulse.vortex.backpressure;

/**
 * Strategy for handling items when backpressure is detected.
 * 
 * <p>Different strategies provide different behaviors:
 * <ul>
 *   <li>ACCEPT: Accept the item and proceed normally</li>
 *   <li>REJECT: Reject with failure callback</li>
 *   <li>DROP: Silently drop the item (no callback, no error)</li>
 * </ul>
 * 
 * <p>Strategies are called synchronously during item submission, so they should
 * be fast and non-blocking. For blocking operations (e.g., throttling), use
 * {@link LifecycleAwareStrategy} instead.
 * 
 * <p>Example implementations:
 * <ul>
 *   <li>{@link DropStrategy}: Silently drops items when backpressure is high</li>
 *   <li>{@link RejectStrategy}: Rejects items with exception when backpressure is high</li>
 *   <li>{@link OverflowStrategy}: Stores items to overflow and handles lifecycle</li>
 * </ul>
 * 
 * @param <T> the type of items being handled
 */
public interface BackpressureStrategy<T> {
    /**
     * Handles an item when backpressure is detected.
     * 
     * <p>This method is called synchronously during {@code MicroBatcher.submit()},
     * so it should be fast and non-blocking. If the strategy needs to perform
     * blocking operations or manage state transitions, implement
     * {@link LifecycleAwareStrategy} instead.
     * 
     * @param context the backpressure context containing the item, backpressure level, and provider
     * @return result indicating how the item was handled (ACCEPT, REJECT, or DROP)
     */
    BackpressureResult<T> handle(BackpressureContext<T> context);
    
    /**
     * Gets the backpressure threshold used by this strategy.
     * 
     * <p>This method is used by the MicroBatcher to determine when to trigger
     * lifecycle callbacks. Strategies that use a threshold should return the
     * threshold value (0.0 to 1.0). Strategies that don't use a threshold
     * should return {@link Double#NaN} or a default value.
     * 
     * <p>Default implementation returns {@link Double#NaN} to indicate no threshold.
     * Built-in strategies ({@link DropStrategy}, {@link RejectStrategy}, {@link OverflowStrategy})
     * override this method to return their configured threshold.
     * 
     * @return the backpressure threshold (0.0 to 1.0), or {@link Double#NaN} if not applicable
     * @since 0.0.4
     */
    default double getThreshold() {
        return Double.NaN;
    }
}

