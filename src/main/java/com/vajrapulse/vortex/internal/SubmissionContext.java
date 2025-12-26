package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.results.BatchResult;
import java.util.concurrent.CompletableFuture;

/**
 * Internal context returned by submission methods containing the batch result future
 * and enqueue result for use by both submit() and submitAsync().
 *
 * @param <T> the type of item being submitted
 * @param batchFuture the CompletableFuture that will complete with the batch result
 * @param enqueueResult the result of the enqueue operation
 */
public record SubmissionContext<T>(
    CompletableFuture<BatchResult<T>> batchFuture,
    EnqueueResult enqueueResult
) {
}
