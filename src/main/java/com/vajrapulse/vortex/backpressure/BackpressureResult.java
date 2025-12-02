package com.vajrapulse.vortex.backpressure;

/**
 * Result of backpressure handling.
 * 
 * <p>Indicates how an item was handled when backpressure was detected:
 * <ul>
 *   <li>ACCEPT: Item accepted, proceed normally with submission</li>
 *   <li>REJECT: Item rejected, return failure callback to caller</li>
 *   <li>DROP: Item dropped silently, no callback (treat as success but don't process)</li>
 * </ul>
 * 
 * @param <T> the type of item
 * @param action the action taken (ACCEPT, REJECT, or DROP)
 * @param item the item that was handled
 * @param reason the exception reason (null for ACCEPT and DROP, non-null for REJECT)
 */
public record BackpressureResult<T>(
    BackpressureAction action,
    T item,
    Exception reason
) {
    /**
     * Creates a result indicating the item was accepted.
     * 
     * @param <T> the type of item
     * @param item the item that was accepted
     * @return an ACCEPT result
     */
    public static <T> BackpressureResult<T> accept(T item) {
        return new BackpressureResult<>(BackpressureAction.ACCEPT, item, null);
    }
    
    /**
     * Creates a result indicating the item was rejected.
     * 
     * @param <T> the type of item
     * @param item the item that was rejected
     * @param reason the exception explaining why it was rejected
     * @return a REJECT result
     */
    public static <T> BackpressureResult<T> reject(T item, Exception reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Rejection reason cannot be null");
        }
        return new BackpressureResult<>(BackpressureAction.REJECT, item, reason);
    }
    
    /**
     * Creates a result indicating the item was dropped.
     * 
     * @param <T> the type of item
     * @param item the item that was dropped
     * @return a DROP result
     */
    public static <T> BackpressureResult<T> drop(T item) {
        return new BackpressureResult<>(BackpressureAction.DROP, item, null);
    }
    
    /**
     * Validates the result.
     * 
     * @throws IllegalArgumentException if the result is invalid (e.g., REJECT without reason)
     */
    public BackpressureResult {
        if (action == BackpressureAction.REJECT && reason == null) {
            throw new IllegalArgumentException("REJECT action requires a non-null reason");
        }
        if (action != BackpressureAction.REJECT && reason != null) {
            throw new IllegalArgumentException("Only REJECT action can have a non-null reason");
        }
    }
}

