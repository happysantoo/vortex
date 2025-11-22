package com.vajrapulse.vortex;

import java.util.concurrent.CompletableFuture;

/**
 * Represents a pending request waiting to be batched.
 * 
 * @param <T> the type of request element
 */
class PendingRequest<T> {
    private final T data;
    private final CompletableFuture<BatchResult<T>> future;
    private final long timestamp;
    
    PendingRequest(T data, CompletableFuture<BatchResult<T>> future) {
        this.data = data;
        this.future = future;
        this.timestamp = System.nanoTime();
    }
    
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

