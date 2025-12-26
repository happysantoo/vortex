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
}
