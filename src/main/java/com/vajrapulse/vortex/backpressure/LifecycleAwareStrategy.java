package com.vajrapulse.vortex.backpressure;

/**
 * Optional interface for strategies that need lifecycle management.
 * 
 * <p>Extends {@link BackpressureStrategy} with lifecycle callbacks that are invoked
 * when backpressure state changes. This allows strategies to manage state transitions,
 * perform setup/teardown operations, and coordinate with external systems.
 * 
 * <p>Lifecycle callbacks are invoked by the MicroBatcher when it detects
 * backpressure state transitions:
 * <ul>
 *   <li>{@link #onBackpressureEntered(BackpressureProvider)}: Called once when backpressure
 *       level crosses threshold from below to above</li>
 *   <li>{@link #onBackpressureActive(BackpressureProvider)}: Called periodically while
 *       backpressure is active (default: every 100ms)</li>
 *   <li>{@link #onBackpressureResolved(BackpressureProvider)}: Called once when backpressure
 *       level crosses threshold from above to below</li>
 * </ul>
 * 
 * <p>Use cases:
 * <ul>
 *   <li>Overflow storage: Initialize overflow, pause consumers, replay items</li>
 *   <li>Throttling: Start/stop throttling mechanisms</li>
 *   <li>Monitoring: Notify external systems of backpressure events</li>
 *   <li>Circuit breakers: Open/close circuit breakers</li>
 * </ul>
 * 
 * <p>Example:
 * <pre>{@code
 * public class OverflowStrategy<T> implements LifecycleAwareStrategy<T> {
 *     @Override
 *     public void onBackpressureEntered(BackpressureProvider provider) {
 *         // Pause Kafka consumer, initialize overflow storage
 *         kafkaConsumer.pause();
 *     }
 *     
 *     @Override
 *     public void onBackpressureResolved(BackpressureProvider provider) {
 *         // Replay items, resume Kafka consumer
 *         replayOverflowItems();
 *         kafkaConsumer.resume();
 *     }
 * }
 * }</pre>
 * 
 * @param <T> the type of items being handled
 */
public interface LifecycleAwareStrategy<T> extends BackpressureStrategy<T> {
    /**
     * Called when backpressure is first detected (enters high state).
     * 
     * <p>This method is called once when the backpressure level crosses the threshold
     * from below to above. Use this to:
     * <ul>
     *   <li>Initialize overflow storage</li>
     *   <li>Pause external consumers (e.g., Kafka)</li>
     *   <li>Open circuit breakers</li>
     *   <li>Notify monitoring systems</li>
     * </ul>
     * 
     * <p>This method is called from a background monitoring thread, so it may block
     * if necessary. However, keep it fast to avoid delaying state transitions.
     * 
     * @param provider the backpressure provider that detected the high pressure
     */
    void onBackpressureEntered(BackpressureProvider provider);
    
    /**
     * Called when backpressure is resolved (exits high state).
     * 
     * <p>This method is called once when the backpressure level crosses the threshold
     * from above to below. Use this to:
     * <ul>
     *   <li>Replay items from overflow storage</li>
     *   <li>Resume external consumers (e.g., Kafka)</li>
     *   <li>Close circuit breakers</li>
     *   <li>Notify monitoring systems</li>
     * </ul>
     * 
     * <p>This method is called from a background monitoring thread, so it may block
     * if necessary. However, keep it fast to avoid delaying state transitions.
     * 
     * @param provider the backpressure provider that detected the resolution
     */
    void onBackpressureResolved(BackpressureProvider provider);
    
    /**
     * Called periodically while backpressure is active.
     * 
     * <p>This method is called at regular intervals (default: every 100ms) while
     * backpressure is active. Use this to:
     * <ul>
     *   <li>Check if conditions are met for gradual replay</li>
     *   <li>Update monitoring metrics</li>
     *   <li>Perform periodic maintenance</li>
     * </ul>
     * 
     * <p>This method is called from a background monitoring thread, so it may block
     * if necessary. However, keep it fast to avoid impacting monitoring frequency.
     * 
     * <p>Default implementation does nothing. Override if periodic checks are needed.
     * 
     * @param provider the backpressure provider
     */
    default void onBackpressureActive(BackpressureProvider provider) {
        // Default: no-op
    }
}

