package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.results.BatchResult;
import java.util.concurrent.CompletableFuture;

/**
 * Internal context returned by submission methods containing the batch result future
 * and enqueue result for use by both submit() and submitAsync().
 *
 * @param <T> the type of item being submitted
 */
public class SubmissionContext<T> {
    public final CompletableFuture<BatchResult<T>> batchFuture;
    public final EnqueueResult enqueueResult;
    
    public SubmissionContext(CompletableFuture<BatchResult<T>> batchFuture, EnqueueResult enqueueResult) {
        this.batchFuture = batchFuture;
        this.enqueueResult = enqueueResult;
    }
}

