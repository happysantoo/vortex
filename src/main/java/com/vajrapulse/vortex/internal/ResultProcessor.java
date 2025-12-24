package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.Backend;
import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.results.FailureEvent;
import com.vajrapulse.vortex.results.SuccessEvent;
import com.vajrapulse.vortex.metrics.MetricsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Processes batch results and maps them back to individual requests.
 *
 * @param <T> the type of item being processed
 */
public class ResultProcessor<T> {
    private static final Logger logger = LoggerFactory.getLogger(ResultProcessor.class);
    
    private final BatcherConfig config;
    private final Backend<T> backend;
    private final MetricsManager metrics;
    private final RetryManager<T> retryManager;
    private final java.util.function.Function<T, CompletableFuture<BatchResult<T>>> submitFunction;
    private final boolean debugMode;
    
    /**
     * Creates a new ResultProcessor.
     *
     * @param config the batcher configuration
     * @param backend the backend for processing batches
     * @param metrics the metrics manager for recording metrics
     * @param retryManager the retry manager for handling retries
     * @param submitFunction function to submit items for retry/replay
     * @param debugMode whether debug mode is enabled
     */
    public ResultProcessor(BatcherConfig config, Backend<T> backend, 
                   MetricsManager metrics, RetryManager<T> retryManager,
                   java.util.function.Function<T, CompletableFuture<BatchResult<T>>> submitFunction,
                   boolean debugMode) {
        this.config = config;
        this.backend = backend;
        this.metrics = metrics;
        this.retryManager = retryManager;
        this.submitFunction = submitFunction;
        this.debugMode = debugMode;
    }
    
    /**
     * Processes batch results and maps them back to individual requests.
     *
     * @param batch the list of pending requests in the batch
     * @param result the batch result from the backend
     */
    public void processResults(List<PendingRequest<T>> batch, BatchResult<T> result) {
        if (config.isAtomicCommit() && !result.isAllSuccess()) {
            processAtomicCommitFailure(batch, result);
        } else {
            processNonAtomicResults(batch, result);
        }
    }
    
    private void processAtomicCommitFailure(List<PendingRequest<T>> batch, BatchResult<T> result) {
        long batchCompletionTime = System.nanoTime();
        RuntimeException atomicError = new RuntimeException("Batch failed due to atomic commit requirement");
        
        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            retryManager.tryRetryOrFail(req.getData(), atomicError, req.getFuture());
        }
    }
    
    /**
     * Processes non-atomic batch results by mapping backend results back to individual requests.
     * 
     * Matching Strategy (Optimized with Hash-Based Lookup):
     * 1. Build hash maps for O(1) lookup instead of O(n) linear search
     * 2. First, attempts to match each request with results by data equality
     * 3. If exact match fails (e.g., backend returns results in different order), uses fallback:
     *    - Distributes remaining successes/failures proportionally to unmatched requests
     *    - This ensures all requests get a result even if backend doesn't maintain order
     * 
     * @param batch the list of pending requests
     * @param result the batch result from backend
     */
    private void processNonAtomicResults(List<PendingRequest<T>> batch, BatchResult<T> result) {
        List<SuccessEvent<T>> successes = result.getSuccesses();
        List<FailureEvent<T>> failures = result.getFailures();

        // Check if replay is needed
        if (!successes.isEmpty() && !failures.isEmpty()) {
            boolean backendWantsReplay = backend.shouldReplaySuccesses(result);
            if (backendWantsReplay || config.isAutoReplaySuccesses()) {
                replaySuccessfulItems(successes);
            }
        }

        Map<T, SuccessEvent<T>> successMap = buildSuccessMap(successes);
        Map<T, FailureEvent<T>> failureMap = buildFailureMap(failures);

        // Track used results for fallback logic
        Map<T, Boolean> usedSuccesses = new HashMap<>();
        Map<T, Boolean> usedFailures = new HashMap<>();

        // Map results back to requests using O(1) hash lookup
        long batchCompletionTime = System.nanoTime();

        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            T data = req.getData();

            boolean matched = tryMatchSuccess(data, successMap, usedSuccesses, req);
            if (!matched) {
                tryMatchFailure(data, failureMap, usedFailures, req);
            }
        }

        List<SuccessEvent<T>> unmatchedSuccesses = collectUnmatchedSuccesses(successes, usedSuccesses);
        List<FailureEvent<T>> unmatchedFailures = collectUnmatchedFailures(failures, usedFailures);

        distributeFallbackResults(batch, usedSuccesses, usedFailures, unmatchedSuccesses, unmatchedFailures);

        // Clean up retry counts for successful items
        for (SuccessEvent<T> success : successes) {
            retryManager.clearRetryCount(success.getData());
        }
    }
    
    private Map<T, SuccessEvent<T>> buildSuccessMap(List<SuccessEvent<T>> successes) {
        // Build hash maps for O(1) lookup (optimization: O(n) -> O(1) per request)
        // Note: If backend returns multiple results with the same data value, only the last one
        // will be retained in the map. This is expected behavior - backends should return unique
        // data values per request, or handle duplicates explicitly.
        Map<T, SuccessEvent<T>> successMap = new HashMap<>();
        for (SuccessEvent<T> success : successes) {
            T data = success.getData();
            if (data != null) {
                successMap.put(data, success);
            }
        }
        return successMap;
    }

    private Map<T, FailureEvent<T>> buildFailureMap(List<FailureEvent<T>> failures) {
        Map<T, FailureEvent<T>> failureMap = new HashMap<>();
        for (FailureEvent<T> failure : failures) {
            T data = failure.getData();
            if (data != null) {
                failureMap.put(data, failure);
            }
        }
        return failureMap;
    }

    private boolean tryMatchSuccess(
            T data,
            Map<T, SuccessEvent<T>> successMap,
            Map<T, Boolean> usedSuccesses,
            PendingRequest<T> req) {

        SuccessEvent<T> success = successMap.get(data);
        if (success != null && !usedSuccesses.getOrDefault(data, false)) {
            metrics.recordRequestSucceeded();
            req.getFuture().complete(new BatchResult<>(
                List.of(success),
                List.of()
            ));
            usedSuccesses.put(data, true);
            return true;
        }
        return false;
    }

    private boolean tryMatchFailure(
            T data,
            Map<T, FailureEvent<T>> failureMap,
            Map<T, Boolean> usedFailures,
            PendingRequest<T> req) {

        FailureEvent<T> failure = failureMap.get(data);
        if (failure != null && !usedFailures.getOrDefault(data, false)) {
            Throwable error = failure.getError();
            retryManager.tryRetryOrFail(data, error, req.getFuture());
            usedFailures.put(data, true);
            return true;
        }
        return false;
    }

    private List<SuccessEvent<T>> collectUnmatchedSuccesses(
            List<SuccessEvent<T>> successes,
            Map<T, Boolean> usedSuccesses) {
        List<SuccessEvent<T>> unmatchedSuccesses = new ArrayList<>();
        for (SuccessEvent<T> s : successes) {
            if (!usedSuccesses.getOrDefault(s.getData(), false)) {
                unmatchedSuccesses.add(s);
            }
        }
        return unmatchedSuccesses;
    }

    private List<FailureEvent<T>> collectUnmatchedFailures(
            List<FailureEvent<T>> failures,
            Map<T, Boolean> usedFailures) {
        List<FailureEvent<T>> unmatchedFailures = new ArrayList<>();
        for (FailureEvent<T> f : failures) {
            if (!usedFailures.getOrDefault(f.getData(), false)) {
                unmatchedFailures.add(f);
            }
        }
        return unmatchedFailures;
    }

    private void distributeFallbackResults(
            List<PendingRequest<T>> batch,
            Map<T, Boolean> usedSuccesses,
            Map<T, Boolean> usedFailures,
            List<SuccessEvent<T>> unmatchedSuccesses,
            List<FailureEvent<T>> unmatchedFailures) {

        // Handle unmatched requests with fallback distribution
        for (PendingRequest<T> req : batch) {
            T data = req.getData();
            boolean wasMatched = usedSuccesses.getOrDefault(data, false)
                || usedFailures.getOrDefault(data, false);

            if (!wasMatched) {
                handleFallback(req, unmatchedSuccesses, unmatchedFailures);
            }
        }
    }
    
    private void handleFallback(PendingRequest<T> req, List<SuccessEvent<T>> unmatchedSuccesses, 
                                List<FailureEvent<T>> unmatchedFailures) {
        if (!unmatchedSuccesses.isEmpty()) {
            // Use first unmatched success and remove it
            // Note: We use req.getData() instead of success.getData() because in fallback mode,
            // the backend result's data doesn't match the request's data (that's why we're in fallback).
            // We preserve the request's identity while using the success status from the backend.
            unmatchedSuccesses.remove(0);
            metrics.recordRequestSucceeded();
            req.getFuture().complete(new BatchResult<>(
                List.of(new SuccessEvent<>(req.getData())),
                List.of()
            ));
        } else if (!unmatchedFailures.isEmpty()) {
            // Use first unmatched failure and remove it
            // Note: We use req.getData() to preserve request identity, but use the error from the failure event
            FailureEvent<T> failure = unmatchedFailures.remove(0);
            Throwable failureError = failure.getError();
            retryManager.tryRetryOrFail(req.getData(), failureError, req.getFuture());
        } else {
            // No unmatched results available - treat as failure
            Throwable failureError = new RuntimeException("Request failed in batch");
            retryManager.tryRetryOrFail(req.getData(), failureError, req.getFuture());
        }
    }
    
    private void recordMetrics(PendingRequest<T> req, long batchCompletionTime) {
        long fullLatency = batchCompletionTime - req.getTimestamp();
        // Record aggregate wait time (for backward compatibility with existing metrics)
        metrics.recordWaitTime(fullLatency);
        
        // Record per-item full latency (submit to completion) if enabled
        if (config.isPerItemMetrics()) {
            metrics.recordItemSubmitLatency(fullLatency);
        }
    }
    
    private void replaySuccessfulItems(List<SuccessEvent<T>> successes) {
        for (SuccessEvent<T> success : successes) {
            try {
                submitFunction.apply(success.getData());
                metrics.recordRequestReplayed();
            } catch (IllegalStateException e) {
                // Batcher is closed, skip replay
                logger.debug("Cannot replay item - batcher is closed", e);
                break;
            } catch (Exception e) {
                logger.warn("Error replaying successful item: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Processes a batch failure by recording metrics and attempting retries for all items.
     *
     * @param batch the list of pending requests in the failed batch
     * @param error the error that caused the batch to fail
     */
    public void processFailure(List<PendingRequest<T>> batch, Throwable error) {
        long batchCompletionTime = System.nanoTime();
        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            retryManager.tryRetryOrFail(req.getData(), error, req.getFuture());
        }
    }
}

