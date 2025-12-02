package com.vajrapulse.vortex.backpressure;

/**
 * Action taken when backpressure is detected.
 */
public enum BackpressureAction {
    /**
     * Accept the item and proceed normally with submission.
     * The item will be queued and processed as usual.
     */
    ACCEPT,
    
    /**
     * Reject the item with a failure callback.
     * The caller will receive an exception via CompletableFuture.
     */
    REJECT,
    
    /**
     * Silently drop the item.
     * No callback is made, and the item is not processed.
     * The caller receives a successful result but the item is not actually processed.
     */
    DROP
}

