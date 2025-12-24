package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.ItemRejectedException;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.metrics.MetricsManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Handles item submission logic, including validation, queueing, and metrics recording.
 *
 * @param <T> the type of item being submitted
 */
public class SubmissionHandler<T> {
    private static final int QUEUE_OFFER_TIMEOUT_MS = 100;
    
    private final BatcherConfig config;
    private final BlockingQueue<PendingRequest<T>> queue;
    private final MetricsManager metrics;
    private final TracingHelper tracingHelper;
    private final Supplier<Boolean> isClosedSupplier;
    private final Supplier<IllegalStateException> closedExceptionSupplier;
    
    public SubmissionHandler(
            BatcherConfig config,
            BlockingQueue<PendingRequest<T>> queue,
            MetricsManager metrics,
            TracingHelper tracingHelper,
            Supplier<Boolean> isClosedSupplier,
            Supplier<IllegalStateException> closedExceptionSupplier) {
        this.config = config;
        this.queue = queue;
        this.metrics = metrics;
        this.tracingHelper = tracingHelper;
        this.isClosedSupplier = isClosedSupplier;
        this.closedExceptionSupplier = closedExceptionSupplier;
    }
    
    /**
     * Common submission logic used by both submit() and submitAsync().
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Validation (closed check, null check)</li>
     *   <li>Tracing hook invocation</li>
     *   <li>Queueing with configurable threshold and timeout behavior</li>
     *   <li>Metrics recording</li>
     *   <li>Future completion for rejections</li>
     * </ul>
     * 
     * @param item the item to submit
     * @param applyThreshold whether to apply queue rejection threshold
     * @param useTimeout whether to use timed offer when enqueuing
     * @return SubmissionContext containing the batch future and enqueue result
     */
    public SubmissionContext<T> submitCommon(T item, boolean applyThreshold, boolean useTimeout) {
        // Validation
        if (isClosedSupplier.get()) {
            CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
            future.completeExceptionally(closedExceptionSupplier.get());
            return new SubmissionContext<>(future, EnqueueResult.REJECTED_FULL);
        }
        
        if (item == null) {
            CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
            future.completeExceptionally(new NullPointerException("Item cannot be null"));
            return new SubmissionContext<>(future, EnqueueResult.REJECTED_FULL);
        }
        
        // Tracing hook
        tracingHelper.safeOnSubmit(item);
        
        // Queueing
        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(item, future);
        
        EnqueueResult enqueueResult = tryEnqueue(request, applyThreshold, useTimeout);
        
        if (enqueueResult == EnqueueResult.REJECTED_THRESHOLD || enqueueResult == EnqueueResult.REJECTED_FULL) {
            metrics.recordRequestRejected();
            int currentSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            future.completeExceptionally(ItemRejectedException.queueFull(currentSize, maxSize));
        } else if (enqueueResult == EnqueueResult.INTERRUPTED) {
            metrics.recordRequestRejected();
            future.completeExceptionally(new InterruptedException("Interrupted while queuing item"));
        } else {
            // Item accepted
            metrics.recordRequestSubmitted();
        }
        
        return new SubmissionContext<>(future, enqueueResult);
    }
    
    /**
     * Attempts to enqueue the given request into the internal queue.
     *
     * @param request        the pending request to enqueue
     * @param applyThreshold whether to apply the configured queue rejection threshold
     * @param useTimeout     whether to use a timed offer when enqueuing
     * @return the outcome of the enqueue attempt
     */
    public EnqueueResult tryEnqueue(PendingRequest<T> request, boolean applyThreshold, boolean useTimeout) {
        int maxSize = config.getMaxQueueSize();

        if (applyThreshold) {
            double threshold = config.getQueueRejectionThreshold();
            int rejectionThreshold = (int) Math.ceil(maxSize * threshold);
            int currentSize = queue.size();
            if (currentSize >= rejectionThreshold) {
                return EnqueueResult.REJECTED_THRESHOLD;
            }
        }

        boolean offered;
        if (useTimeout) {
            try {
                offered = queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return EnqueueResult.INTERRUPTED;
            }
        } else {
            offered = queue.offer(request);
        }

        if (!offered) {
            return EnqueueResult.REJECTED_FULL;
        }

        return EnqueueResult.ACCEPTED;
    }
}

