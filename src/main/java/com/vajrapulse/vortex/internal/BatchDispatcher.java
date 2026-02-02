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
    private final CircuitBreaker circuitBreaker;

    /**
     * Creates a new BatchDispatcher.
     *
     * @param config the batcher configuration
     * @param backend the backend for dispatching batches
     * @param executor the executor service for dispatching batches
     * @param metrics the metrics manager for recording metrics
     * @param resultProcessor the result processor for processing batch results
     * @param dispatchSemaphore the semaphore for limiting concurrent dispatches (may be null)
     * @param activeBatchCount the atomic integer for tracking active batches (may be null)
     * @param tracingHelper the tracing helper for invoking tracing hooks
     * @param debugMode whether debug mode is enabled
     * @param circuitBreaker optional circuit breaker for backend resilience (may be null)
     */
    public BatchDispatcher(
            BatcherConfig config,
            Backend<T> backend,
            ExecutorService executor,
            MetricsManager metrics,
            ResultProcessor<T> resultProcessor,
            Semaphore dispatchSemaphore,
            AtomicInteger activeBatchCount,
            TracingHelper tracingHelper,
            boolean debugMode,
            CircuitBreaker circuitBreaker) {
        this.config = config;
        this.backend = backend;
        this.executor = executor;
        this.metrics = metrics;
        this.resultProcessor = resultProcessor;
        this.dispatchSemaphore = dispatchSemaphore;
        this.activeBatchCount = activeBatchCount;
        this.tracingHelper = tracingHelper;
        this.debugMode = debugMode;
        this.circuitBreaker = circuitBreaker;
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
                // Record backpressure metric for each item in the batch
                for (int i = 0; i < batch.size(); i++) {
                    metrics.recordBackpressureConcurrentHit();
                }
                logger.debug("Batch rejected: too many concurrent batches (limit: {})", config.getMaxConcurrentBatches());
                handleDispatchRejection(batch);
                return;
            }
        }
        
        // Build data list once so it can be reused for dispatch, metrics, and tracing
        List<T> dataList = new ArrayList<>(batch.size());
        for (PendingRequest<T> req : batch) {
            dataList.add(req.data());
        }

        tracingHelper.safeOnBatchDispatchStart(dataList);
        
        metrics.recordBatchDispatched();
        
        // Calculate average wait time inline (optimization: avoid stream overhead)
        if (debugMode) {
            long totalWait = 0;
            long now = System.nanoTime();
            for (PendingRequest<T> req : batch) {
                totalWait += now - req.timestamp();
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
                long queueWaitTime = dispatchStartTime - req.timestamp();
                metrics.recordQueueWaitTime(queueWaitTime);
            }
        }
        
        // Execute backend dispatch on a virtual thread
        try {
            // Count this batch as in-flight once it is scheduled (avoids a window where work is in-flight but gauge is 0).
            if (activeBatchCount != null) {
                activeBatchCount.incrementAndGet();
            }
            executor.submit(() -> {
                try {
                    // Re-check circuit inside the task; it may have opened since we submitted
                    if (circuitBreaker != null && !circuitBreaker.allowRequest()) {
                        handleCircuitOpen(batch);
                        return; // finally block below releases semaphore and decrements activeBatchCount
                    }
                    logger.debug("Calling backend.dispatch() for batch of size: {}", dataList.size());
                    BatchResult<T> result = backend.dispatch(dataList);
                    if (circuitBreaker != null) {
                        circuitBreaker.recordSuccess();
                    }
                    metrics.recordBatchDispatchLatency(sample);
                    tracingHelper.safeOnBatchDispatchSuccess(dataList, result);
                    logger.debug("Backend dispatch completed: successes={}, failures={}", 
                        result.getSuccesses().size(), result.getFailures().size());
                    resultProcessor.processResults(batch, result);
                } catch (Exception e) {
                    if (circuitBreaker != null) {
                        circuitBreaker.recordFailure();
                    }
                    metrics.recordBatchDispatchLatency(sample);
                    tracingHelper.safeOnBatchDispatchFailure(dataList, e);
                    logger.debug("Backend dispatch failed", e);
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
            // Executor rejected - release permit and revert active batch count
            if (dispatchSemaphore != null) {
                dispatchSemaphore.release();
            }
            if (activeBatchCount != null) {
                activeBatchCount.decrementAndGet();
            }
            logger.debug("Executor rejected batch dispatch", e);
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
            CompletableFuture<BatchResult<T>> future = request.future();
            if (future != null && !future.isDone()) {
                future.completeExceptionally(rejectionError);
            }
        }
    }

    /**
     * Handles rejection of a batch because the circuit breaker is open.
     */
    private void handleCircuitOpen(List<PendingRequest<T>> batch) {
        ItemRejectedException error = ItemRejectedException.circuitOpen();
        for (PendingRequest<T> request : batch) {
            CompletableFuture<BatchResult<T>> future = request.future();
            if (future != null && !future.isDone()) {
                future.completeExceptionally(error);
            }
        }
    }
}

