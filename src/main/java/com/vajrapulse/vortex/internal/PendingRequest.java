package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.results.BatchResult;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a pending request waiting to be batched.
 * 
 * @param <T> the type of request element
 * @param data the request data
 * @param future the CompletableFuture that will be completed with the batch result
 * @param timestamp the timestamp when the request was created (nanoseconds)
 */
public record PendingRequest<T>(
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
    public PendingRequest(T data, CompletableFuture<BatchResult<T>> future) {
        this(data, future, System.nanoTime());
    }
    
    // Convenience getters for backward compatibility (Records generate data(), future(), timestamp() automatically)
    // These are optional but make the migration smoother
    
    /**
     * Gets the request data.
     *
     * @return the request data
     */
    public T getData() {
        return data;
    }
    
    /**
     * Gets the CompletableFuture that will be completed with the batch result.
     *
     * @return the CompletableFuture for the batch result
     */
    public CompletableFuture<BatchResult<T>> getFuture() {
        return future;
    }
    
    /**
     * Gets the timestamp when the request was created.
     *
     * @return the timestamp in nanoseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
}
