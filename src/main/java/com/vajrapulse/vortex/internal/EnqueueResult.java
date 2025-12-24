package com.vajrapulse.vortex.internal;

/**
 * Result of attempting to enqueue a pending request into the internal queue.
 */
public enum EnqueueResult {
    ACCEPTED,
    REJECTED_THRESHOLD,
    REJECTED_FULL,
    INTERRUPTED
}

