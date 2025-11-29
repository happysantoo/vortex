package com.vajrapulse.vortex;

import java.util.List;

/**
 * Hook interface for integrating tracing and observability tools with the {@link MicroBatcher}.
 *
 * <p>This interface is intentionally minimal and has no dependencies on any specific
 * tracing implementation (such as OpenTelemetry). Applications can implement this
 * interface to bridge Vortex events into their tracing/observability stack.
 *
 * <p>All methods on this interface are best-effort only. The {@link MicroBatcher}
 * will catch and log any exceptions thrown by hook implementations and will not
 * allow them to affect normal batch processing.
 *
 * @since 0.0.3
 */
public interface BatchTracingHook {

    /**
     * Called when a new item is submitted to the batcher.
     *
     * @param item the submitted item
     */
    void onSubmit(Object item);

    /**
     * Called when a batch is about to be dispatched to the backend.
     *
     * <p>The list contains the data items for the batch (not the internal
     * pending request objects).
     *
     * @param batchItems the items in the batch
     */
    void onBatchDispatchStart(List<?> batchItems);

    /**
     * Called when a batch has been successfully dispatched to the backend.
     *
     * @param batchItems the items in the batch
     * @param result     the batch result from the backend
     */
    void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result);

    /**
     * Called when a batch dispatch has failed.
     *
     * @param batchItems the items in the batch
     * @param error      the error that caused the failure
     */
    void onBatchDispatchFailure(List<?> batchItems, Throwable error);

    /**
     * Called when a retry is scheduled for a specific item.
     *
     * @param item  the item being retried
     * @param cause the error that triggered the retry
     */
    void onRetry(Object item, Throwable cause);
}


