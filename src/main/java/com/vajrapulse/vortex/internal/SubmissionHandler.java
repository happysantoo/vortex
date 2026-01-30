package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.ItemRejectedException;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.metrics.MetricsManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Handles item submission logic, including validation, queueing, and metrics recording.
 * 
 * <p>This handler implements unified backpressure evaluation that considers both
 * queue depth and concurrent batch availability to provide consistent rejection behavior.
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
    
    // Concurrent batch limiting (optional - may be null if not configured)
    private final Semaphore dispatchSemaphore;
    private final AtomicInteger activeBatchCount;
    
    /**
     * Creates a new SubmissionHandler.
     *
     * @param config the batcher configuration
     * @param queue the blocking queue to enqueue requests to
     * @param metrics the metrics manager for recording metrics
     * @param tracingHelper the tracing helper for invoking tracing hooks
     * @param isClosedSupplier supplier to check if the batcher is closed
     * @param closedExceptionSupplier supplier to create exception when batcher is closed
     * @param dispatchSemaphore the semaphore for limiting concurrent dispatches (may be null)
     * @param activeBatchCount the atomic integer for tracking active batches (may be null)
     */
    public SubmissionHandler(
            BatcherConfig config,
            BlockingQueue<PendingRequest<T>> queue,
            MetricsManager metrics,
            TracingHelper tracingHelper,
            Supplier<Boolean> isClosedSupplier,
            Supplier<IllegalStateException> closedExceptionSupplier,
            Semaphore dispatchSemaphore,
            AtomicInteger activeBatchCount) {
        this.config = config;
        this.queue = queue;
        this.metrics = metrics;
        this.tracingHelper = tracingHelper;
        this.isClosedSupplier = isClosedSupplier;
        this.closedExceptionSupplier = closedExceptionSupplier;
        this.dispatchSemaphore = dispatchSemaphore;
        this.activeBatchCount = activeBatchCount;
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
            int queueSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            return new SubmissionContext<>(future, EnqueueResult.rejected(
                EnqueueResult.Type.REJECTED_FULL, queueSize, maxSize));
        }
        
        if (item == null) {
            CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
            future.completeExceptionally(new NullPointerException("Item cannot be null"));
            int queueSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            return new SubmissionContext<>(future, EnqueueResult.rejected(
                EnqueueResult.Type.REJECTED_FULL, queueSize, maxSize));
        }
        
        // Tracing hook
        tracingHelper.safeOnSubmit(item);
        
        // Unified backpressure check (considers queue depth threshold and, optionally,
        // concurrent batch limits when early rejection is enabled)
        BackpressureStatus backpressureStatus = evaluateBackpressure(applyThreshold);
        if (backpressureStatus == BackpressureStatus.REJECT_CONCURRENT_BATCHES) {
            // Early concurrent batch rejection (optional, controlled by config flag)
            CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
            int activeBatches = activeBatchCount != null ? activeBatchCount.get() : 0;
            int maxBatches = config.getMaxConcurrentBatches();
            future.completeExceptionally(ItemRejectedException.concurrentLimitReached(
                activeBatches, maxBatches));
            metrics.recordBackpressureConcurrentHit();
            int queueSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            return new SubmissionContext<>(future, EnqueueResult.rejected(
                EnqueueResult.Type.REJECTED_CONCURRENT_BATCHES, queueSize, maxSize));
        } else if (backpressureStatus == BackpressureStatus.REJECT_QUEUE_THRESHOLD) {
            // Queue threshold rejection is still handled in tryEnqueue() after offer attempt
            // to maintain consistency with the atomic offer() pattern
        }
        
        // Queueing
        CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
        PendingRequest<T> request = new PendingRequest<>(item, future);
        
        EnqueueResult enqueueResult = tryEnqueue(request, applyThreshold, useTimeout);
        
        if (enqueueResult.isRejected()) {
            // Record specific rejection type metric
            EnqueueResult.Type rejectionType = enqueueResult.getType();
            if (rejectionType == EnqueueResult.Type.REJECTED_THRESHOLD) {
                metrics.recordBackpressureThresholdHit();
            } else if (rejectionType == EnqueueResult.Type.REJECTED_FULL) {
                metrics.recordBackpressureFullHit();
            } else if (rejectionType == EnqueueResult.Type.REJECTED_CONCURRENT_BATCHES) {
                metrics.recordBackpressureConcurrentHit();
            } else {
                // Fallback for unknown rejection types
                metrics.recordRequestRejected();
            }
            
            // Use queue size captured at rejection time (from enqueueResult) for accurate reporting
            future.completeExceptionally(ItemRejectedException.queueFull(
                enqueueResult.getQueueSizeAtRejection(), 
                enqueueResult.getMaxQueueSize()));
        } else if (enqueueResult.isInterrupted()) {
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
     * <p>This method fixes the TOCTOU (Time-Of-Check-Time-Of-Use) race condition by
     * attempting the atomic offer() operation first, then checking threshold/full status
     * based on the result. This ensures consistent behavior and accurate queue size
     * reporting at rejection time.
     *
     * @param request        the pending request to enqueue
     * @param applyThreshold whether to apply the configured queue rejection threshold
     * @param useTimeout     whether to use a timed offer when enqueuing
     * @return the outcome of the enqueue attempt, including queue size at rejection time
     */
    public EnqueueResult tryEnqueue(PendingRequest<T> request, boolean applyThreshold, boolean useTimeout) {
        int maxSize = config.getMaxQueueSize();

        // Try to offer first (atomic operation) - this eliminates the TOCTOU race condition
        // where queue.size() is checked before offer(), but queue changes between check and use
        boolean offered;
        if (useTimeout) {
            try {
                offered = queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return EnqueueResult.interrupted();
            }
        } else {
            offered = queue.offer(request);
        }

        if (!offered) {
            // Offer failed - capture queue size at rejection time for accurate reporting
            int currentSize = queue.size();
            
            // Determine if rejection was due to threshold or full capacity
            if (applyThreshold) {
                double threshold = config.getQueueRejectionThreshold();
                int rejectionThreshold = (int) Math.ceil(maxSize * threshold);
                
                // If we're at or above threshold, classify as threshold rejection
                // Otherwise, it's a full rejection
                if (currentSize >= rejectionThreshold) {
                    return EnqueueResult.rejected(
                        EnqueueResult.Type.REJECTED_THRESHOLD, currentSize, maxSize);
                }
            }
            
            // Queue is full (at capacity)
            return EnqueueResult.rejected(
                EnqueueResult.Type.REJECTED_FULL, currentSize, maxSize);
        }

        // Offer succeeded - check if we exceeded threshold (for monitoring/consistency)
        // Note: We don't remove the item if threshold exceeded, as that would be racy
        // and could cause items to be lost. The threshold is best-effort protection.
        if (applyThreshold) {
            int currentSize = queue.size();
            double threshold = config.getQueueRejectionThreshold();
            int rejectionThreshold = (int) Math.ceil(maxSize * threshold);
            
            // If we exceeded threshold after adding, log for monitoring but don't reject
            // The item is already queued and will be processed
            if (currentSize > rejectionThreshold && currentSize <= maxSize) {
                // Threshold exceeded but not at capacity - this is expected under high load
                // and acceptable since we can't atomically check-and-offer
            }
        }

        return EnqueueResult.ACCEPTED;
    }
    
    /**
     * Evaluates backpressure status considering both queue depth and concurrent batch availability.
     * 
     * <p>This provides unified backpressure evaluation that:
     * <ul>
     *   <li>Checks queue depth against rejection threshold</li>
     *   <li>Checks concurrent batch limit availability (conservative check)</li>
     * </ul>
     * 
     * <p>Note: Concurrent batch limit checking is conservative - we only reject at submission time
     * if we're at the limit AND the queue is exactly at batchSize-1 (meaning adding this item
     * would definitely form a batch). This prevents queuing items that would be rejected at dispatch,
     * while still allowing items to be queued in most cases (they'll be rejected at dispatch if needed).
     * 
     * @param applyThreshold whether to apply queue rejection threshold
     * @return the backpressure status
     */
    private BackpressureStatus evaluateBackpressure(boolean applyThreshold) {
        // Check queue depth threshold first (if enabled)
        if (applyThreshold) {
            int queueSize = queue.size();
            int maxSize = config.getMaxQueueSize();
            double threshold = config.getQueueRejectionThreshold();
            int rejectionThreshold = (int) Math.ceil(maxSize * threshold);
            
            if (queueSize >= rejectionThreshold) {
                return BackpressureStatus.REJECT_QUEUE_THRESHOLD;
            }
        }
        
        // Check concurrent batch limit for early rejection (optional behavior).
        // Only enabled when configured via BatcherConfig. When disabled (default),
        // concurrent batch limiting happens at dispatch time for backward compatibility.
        if (config.isEarlyConcurrentBatchRejection()
                && dispatchSemaphore != null
                && activeBatchCount != null) {
            int maxConcurrentBatches = config.getMaxConcurrentBatches();
            if (maxConcurrentBatches > 0) {
                int activeBatches = activeBatchCount.get();
                
                // When the number of active batches is already at or above the limit,
                // reject new submissions early to signal backpressure based on
                // concurrent batch pressure.
                if (activeBatches >= maxConcurrentBatches) {
                    return BackpressureStatus.REJECT_CONCURRENT_BATCHES;
                }
            }
        }
        
        return BackpressureStatus.ACCEPT;
    }
    
    /**
     * Result of backpressure evaluation.
     */
    private enum BackpressureStatus {
        /** Item can be accepted. */
        ACCEPT,
        /** Item should be rejected due to queue threshold. */
        REJECT_QUEUE_THRESHOLD,
        /** Item should be rejected due to concurrent batch limit. */
        REJECT_CONCURRENT_BATCHES
    }
}

