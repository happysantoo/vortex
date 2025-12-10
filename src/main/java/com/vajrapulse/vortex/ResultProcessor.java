package com.vajrapulse.vortex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Processes batch results and maps them back to individual requests.
 */
class ResultProcessor<T> {
    private static final Logger logger = LoggerFactory.getLogger(ResultProcessor.class);
    
    private final BatcherConfig config;
    private final Backend<T> backend;
    private final MetricsManager metrics;
    private final RetryManager<T> retryManager;
    private final java.util.function.Function<T, CompletableFuture<BatchResult<T>>> submitFunction;
    private final boolean debugMode;
    
    ResultProcessor(BatcherConfig config, Backend<T> backend, 
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
    
    void processResults(List<PendingRequest<T>> batch, BatchResult<T> result) {
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
        
        Map<T, FailureEvent<T>> failureMap = new HashMap<>();
        for (FailureEvent<T> failure : failures) {
            T data = failure.getData();
            if (data != null) {
                failureMap.put(data, failure);
            }
        }
        
        // Track used results for fallback logic
        Map<T, Boolean> usedSuccesses = new HashMap<>();
        Map<T, Boolean> usedFailures = new HashMap<>();
        
        // Map results back to requests using O(1) hash lookup
        long batchCompletionTime = System.nanoTime();
        
        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            
            T data = req.getData();
            boolean matched = false;
            
            // Try to match with success (O(1) lookup)
            SuccessEvent<T> success = successMap.get(data);
            if (success != null && !usedSuccesses.getOrDefault(data, false)) {
                metrics.recordRequestSucceeded();
                req.getFuture().complete(new BatchResult<>(
                    List.of(success),
                    List.of()
                ));
                usedSuccesses.put(data, true);
                matched = true;
            }
            
            // Try to match with failure if not already matched (O(1) lookup)
            if (!matched) {
                FailureEvent<T> failure = failureMap.get(data);
                if (failure != null && !usedFailures.getOrDefault(data, false)) {
                    Throwable error = failure.getError();
                    retryManager.tryRetryOrFail(data, error, req.getFuture());
                    usedFailures.put(data, true);
                    matched = true;
                }
            }
        }
        
        // Collect unmatched results once after all exact matches are found
        List<SuccessEvent<T>> unmatchedSuccesses = new ArrayList<>();
        for (SuccessEvent<T> s : successes) {
            if (!usedSuccesses.getOrDefault(s.getData(), false)) {
                unmatchedSuccesses.add(s);
            }
        }
        
        List<FailureEvent<T>> unmatchedFailures = new ArrayList<>();
        for (FailureEvent<T> f : failures) {
            if (!usedFailures.getOrDefault(f.getData(), false)) {
                unmatchedFailures.add(f);
            }
        }
        
        // Handle unmatched requests with fallback distribution
        for (PendingRequest<T> req : batch) {
            // Check if this request was matched in the first pass
            T data = req.getData();
            boolean wasMatched = usedSuccesses.getOrDefault(data, false) || 
                                usedFailures.getOrDefault(data, false);
            
            if (!wasMatched) {
                handleFallback(req, unmatchedSuccesses, unmatchedFailures);
            }
        }
        
        // Clean up retry counts for successful items
        for (SuccessEvent<T> success : successes) {
            retryManager.clearRetryCount(success.getData());
        }
    }
    
    // Removed tryMatchSuccess and tryMatchFailure - now using hash-based lookup in processNonAtomicResults
    
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
    
    void processFailure(List<PendingRequest<T>> batch, Throwable error) {
        long batchCompletionTime = System.nanoTime();
        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            retryManager.tryRetryOrFail(req.getData(), error, req.getFuture());
        }
    }
}

