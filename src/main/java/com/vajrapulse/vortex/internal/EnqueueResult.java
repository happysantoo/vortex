package com.vajrapulse.vortex.internal;

/**
 * Result of attempting to enqueue a pending request into the internal queue.
 */
public enum EnqueueResult {
    /** The request was accepted and enqueued. */
    ACCEPTED,
    /** The request was rejected because the queue reached the rejection threshold. */
    REJECTED_THRESHOLD,
    /** The request was rejected because the queue is full. */
    REJECTED_FULL,
    /** The request was interrupted while attempting to enqueue. */
    INTERRUPTED
}

