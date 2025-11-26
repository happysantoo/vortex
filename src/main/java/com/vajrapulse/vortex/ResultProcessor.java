package com.vajrapulse.vortex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    
    ResultProcessor(BatcherConfig config, Backend<T> backend, 
                   MetricsManager metrics, RetryManager<T> retryManager,
                   java.util.function.Function<T, CompletableFuture<BatchResult<T>>> submitFunction) {
        this.config = config;
        this.backend = backend;
        this.metrics = metrics;
        this.retryManager = retryManager;
        this.submitFunction = submitFunction;
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
            
            if (retryManager.shouldRetry(req.getData(), atomicError)) {
                retryManager.scheduleRetry(req.getData(), atomicError, req.getFuture());
            } else {
                metrics.recordRequestFailed();
                req.getFuture().complete(new BatchResult<>(
                    List.of(),
                    List.of(new FailureEvent<>(req.getData(), atomicError))
                ));
            }
        }
    }
    
    /**
     * Processes non-atomic batch results by mapping backend results back to individual requests.
     * 
     * Matching Strategy:
     * 1. First, attempts to match each request with results by data equality (assumes backend maintains order)
     * 2. Matches successes first, then failures, using index-based iteration
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
        
        // Map results back to requests
        long batchCompletionTime = System.nanoTime();
        int successIdx = 0;
        int failureIdx = 0;
        
        for (PendingRequest<T> req : batch) {
            recordMetrics(req, batchCompletionTime);
            
            boolean matched = tryMatchSuccess(req, successes, successIdx);
            if (matched) {
                successIdx++;
                continue;
            }
            
            matched = tryMatchFailure(req, failures, failureIdx);
            if (matched) {
                failureIdx++;
                continue;
            }
            
            // Fallback: distribute proportionally
            handleFallback(req, successes, failures, successIdx, failureIdx);
            if (successIdx < successes.size()) {
                successIdx++;
            } else if (failureIdx < failures.size()) {
                failureIdx++;
            }
        }
        
        // Clean up retry counts for successful items
        for (SuccessEvent<T> success : successes) {
            retryManager.clearRetryCount(success.getData());
        }
    }
    
    private boolean tryMatchSuccess(PendingRequest<T> req, List<SuccessEvent<T>> successes, int successIdx) {
        if (successIdx < successes.size()) {
            T successData = successes.get(successIdx).getData();
            if (successData != null && successData.equals(req.getData())) {
                metrics.recordRequestSucceeded();
                req.getFuture().complete(new BatchResult<>(
                    List.of(successes.get(successIdx)),
                    List.of()
                ));
                return true;
            }
        }
        return false;
    }
    
    private boolean tryMatchFailure(PendingRequest<T> req, List<FailureEvent<T>> failures, int failureIdx) {
        if (failureIdx < failures.size()) {
            T failureData = failures.get(failureIdx).getData();
            if (failureData != null && failureData.equals(req.getData())) {
                FailureEvent<T> failure = failures.get(failureIdx);
                Throwable error = failure.getError();
                
                if (retryManager.shouldRetry(req.getData(), error)) {
                    retryManager.scheduleRetry(req.getData(), error, req.getFuture());
                } else {
                    metrics.recordRequestFailed();
                    req.getFuture().complete(new BatchResult<>(
                        List.of(),
                        List.of(failure)
                    ));
                }
                return true;
            }
        }
        return false;
    }
    
    private void handleFallback(PendingRequest<T> req, List<SuccessEvent<T>> successes, 
                                List<FailureEvent<T>> failures, int successIdx, int failureIdx) {
        if (successIdx < successes.size()) {
            metrics.recordRequestSucceeded();
            req.getFuture().complete(new BatchResult<>(
                List.of(new SuccessEvent<>(req.getData())),
                List.of()
            ));
        } else {
            Throwable failureError = failureIdx < failures.size() ?
                failures.get(failureIdx).getError() :
                new RuntimeException("Request failed in batch");
            
            if (retryManager.shouldRetry(req.getData(), failureError)) {
                retryManager.scheduleRetry(req.getData(), failureError, req.getFuture());
            } else {
                metrics.recordRequestFailed();
                req.getFuture().complete(new BatchResult<>(
                    List.of(),
                    List.of(new FailureEvent<>(req.getData(), failureError))
                ));
            }
        }
    }
    
    private void recordMetrics(PendingRequest<T> req, long batchCompletionTime) {
        long waitTime = batchCompletionTime - req.getTimestamp();
        metrics.recordWaitTime(waitTime);
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
            
            if (retryManager.shouldRetry(req.getData(), error)) {
                retryManager.scheduleRetry(req.getData(), error, req.getFuture());
            } else {
                metrics.recordRequestFailed();
                req.getFuture().complete(new BatchResult<>(
                    List.of(),
                    List.of(new FailureEvent<>(req.getData(), error))
                ));
            }
        }
    }
}

