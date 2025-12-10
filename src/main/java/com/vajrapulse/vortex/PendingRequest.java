package com.vajrapulse.vortex;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a pending request waiting to be batched.
 * 
 * @param <T> the type of request element
 * @param data the request data
 * @param future the CompletableFuture that will be completed with the batch result
 * @param timestamp the timestamp when the request was created (nanoseconds)
 */
record PendingRequest<T>(
    T data,
    CompletableFuture<BatchResult<T>> future,
    long timestamp
) {
    /**
     * Creates a new PendingRequest with the current timestamp.
     * 
     * @param data the request data
     * @param future the CompletableFuture that will be completed with the batch result
     */
    PendingRequest(T data, CompletableFuture<BatchResult<T>> future) {
        this(data, future, System.nanoTime());
    }
    
    // Convenience getters for backward compatibility (Records generate data(), future(), timestamp() automatically)
    // These are optional but make the migration smoother
    T getData() {
        return data;
    }
    
    CompletableFuture<BatchResult<T>> getFuture() {
        return future;
    }
    
    long getTimestamp() {
        return timestamp;
    }
}
