package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.Backend;
import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.ItemRejectedException;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.metrics.MetricsManager;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles batch dispatch logic, including concurrent dispatch limiting, metrics recording,
 * tracing hook invocations, and backend execution.
 *
 * @param <T> the type of item being dispatched
 */
public class BatchDispatcher<T> {
    private static final Logger logger = LoggerFactory.getLogger(BatchDispatcher.class);
    
    private final BatcherConfig config;
    private final Backend<T> backend;
    private final ExecutorService executor;
    private final MetricsManager metrics;
    private final ResultProcessor<T> resultProcessor;
    private final Semaphore dispatchSemaphore;
    private final AtomicInteger activeBatchCount;
    private final TracingHelper tracingHelper;
    private final boolean debugMode;
    
    public BatchDispatcher(
            BatcherConfig config,
            Backend<T> backend,
            ExecutorService executor,
            MetricsManager metrics,
            ResultProcessor<T> resultProcessor,
            Semaphore dispatchSemaphore,
            AtomicInteger activeBatchCount,
            TracingHelper tracingHelper,
            boolean debugMode) {
        this.config = config;
        this.backend = backend;
        this.executor = executor;
        this.metrics = metrics;
        this.resultProcessor = resultProcessor;
        this.dispatchSemaphore = dispatchSemaphore;
        this.activeBatchCount = activeBatchCount;
        this.tracingHelper = tracingHelper;
        this.debugMode = debugMode;
    }
    
    /**
     * Dispatches a batch of pending requests to the backend.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Concurrent dispatch limiting (semaphore acquisition)</li>
     *   <li>Tracing hook invocations</li>
     *   <li>Metrics recording</li>
     *   <li>Backend dispatch execution</li>
     *   <li>Result processing</li>
     *   <li>Resource cleanup (semaphore release, activeBatchCount)</li>
     * </ul>
     * 
     * @param batch the batch of pending requests to dispatch
     */
    public void dispatchBatch(List<PendingRequest<T>> batch) {
        if (batch.isEmpty()) {
            return;
        }
        
        // Try to acquire permit if limit is configured
        boolean acquired = true;
        if (dispatchSemaphore != null) {
            acquired = dispatchSemaphore.tryAcquire();
            if (!acquired) {
                // Can't dispatch - too many concurrent batches
                metrics.recordDispatchRejected();
                if (debugMode) {
                    logger.debug("Batch rejected: too many concurrent batches (limit: {})", config.getMaxConcurrentBatches());
                }
                handleDispatchRejection(batch);
                return;
            }
        }
        
        // Build data list once so it can be reused for dispatch, metrics, and tracing
        List<T> dataList = new ArrayList<>(batch.size());
        for (PendingRequest<T> req : batch) {
            dataList.add(req.getData());
        }
        
        tracingHelper.safeOnBatchDispatchStart(dataList);
        
        metrics.recordBatchDispatched();
        
        // Calculate average wait time inline (optimization: avoid stream overhead)
        if (debugMode) {
            long totalWait = 0;
            long now = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                totalWait += now - req.getTimestamp();
            }
            long avgWaitTime = totalWait / batch.size();
            logger.debug("Dispatching batch: size={}, avgWaitTimeNs={}", batch.size(), avgWaitTime);
        }
        
        metrics.recordBatchSize(batch.size());
        
        Timer.Sample sample = metrics.startBatchDispatchTimer();
        
        // Record per-item batch size and queue wait time if enabled
        if (config.isPerItemMetrics()) {
            metrics.recordItemBatchSize(batch.size());
            
            // Record queue wait time for each item (from submit to batch dispatch start)
            long dispatchStartTime = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                long queueWaitTime = dispatchStartTime - req.getTimestamp();
                metrics.recordQueueWaitTime(queueWaitTime);
            }
        }
        
        // Execute backend dispatch on a virtual thread
        try {
            executor.submit(() -> {
                // Update active batch count after successful submission
                if (activeBatchCount != null) {
                    activeBatchCount.incrementAndGet();
                }
                try {
                    if (debugMode) {
                        logger.debug("Calling backend.dispatch() for batch of size: {}", dataList.size());
                    }
                    BatchResult<T> result = backend.dispatch(dataList);
                    metrics.recordBatchDispatchLatency(sample);
                    tracingHelper.safeOnBatchDispatchSuccess(dataList, result);
                    if (debugMode) {
                        logger.debug("Backend dispatch completed: successes={}, failures={}", 
                            result.getSuccesses().size(), result.getFailures().size());
                    }
                    resultProcessor.processResults(batch, result);
                } catch (Exception e) {
                    metrics.recordBatchDispatchLatency(sample);
                    tracingHelper.safeOnBatchDispatchFailure(dataList, e);
                    if (debugMode) {
                        logger.debug("Backend dispatch failed", e);
                    }
                    resultProcessor.processFailure(batch, e);
                } finally {
                    // Release permit when done
                    if (dispatchSemaphore != null) {
                        dispatchSemaphore.release();
                    }
                    // Update active batch count
                    if (activeBatchCount != null) {
                        activeBatchCount.decrementAndGet();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // Executor rejected - release permit (activeBatchCount was never incremented)
            if (dispatchSemaphore != null) {
                dispatchSemaphore.release();
            }
            if (debugMode) {
                logger.debug("Executor rejected batch dispatch", e);
            }
            handleDispatchRejection(batch);
        }
    }
    
    /**
     * Handles rejection of a batch due to concurrent dispatch limit.
     * 
     * <p>When a batch cannot be dispatched due to the concurrent batch limit,
     * the items in the batch are rejected individually. Each item's future
     * will be notified of the rejection.
     * 
     * @param batch the batch that was rejected
     */
    private void handleDispatchRejection(List<PendingRequest<T>> batch) {
        int activeBatches = activeBatchCount != null ? activeBatchCount.get() : 0;
        ItemRejectedException rejectionError = ItemRejectedException.concurrentLimitReached(
            activeBatches, config.getMaxConcurrentBatches());
        
        for (PendingRequest<T> request : batch) {
            CompletableFuture<BatchResult<T>> future = request.getFuture();
            if (future != null && !future.isDone()) {
                future.completeExceptionally(rejectionError);
            }
        }
    }
}

