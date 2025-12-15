package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.results.FailureEvent;
import com.vajrapulse.vortex.results.SuccessEvent;
import com.vajrapulse.vortex.metrics.MetricsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Manages retry logic for failed items.
 *
 * <p>This class tracks retry counts for items and schedules retries with configurable delays.
 * To prevent memory leaks, the retry counts map is periodically cleaned up based on the
 * configured {@code maxRetries} value.
 */
public class RetryManager<T> {
    private static final Logger logger = LoggerFactory.getLogger(RetryManager.class);
    
    // Cleanup interval for stale retry entries (5 minutes)
    private static final long CLEANUP_INTERVAL_MINUTES = 5;
    
    private final BatcherConfig config;
    private final ExecutorService executor;
    private final Function<T, CompletableFuture<BatchResult<T>>> submitFunction;
    private final java.util.function.Supplier<Boolean> isClosedSupplier;
    private final MetricsManager metrics;
    private final ConcurrentHashMap<T, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final boolean debugMode;
    
    public RetryManager(BatcherConfig config, ExecutorService executor,
                 Function<T, CompletableFuture<BatchResult<T>>> submitFunction,
                 java.util.function.Supplier<Boolean> isClosedSupplier,
                 MetricsManager metrics,
                 boolean debugMode) {
        this.config = config;
        this.executor = executor;
        this.submitFunction = submitFunction;
        this.isClosedSupplier = isClosedSupplier;
        this.metrics = metrics;
        this.debugMode = debugMode;
        
        if (config.getMaxRetries() > 0) {
            // Start periodic cleanup of retry entries associated with items that
            // have already reached the max retry count. This keeps the map from
            // growing unbounded in long‑lived applications without relying on
            // size‑based eviction heuristics.
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vortex-retry-cleanup");
                t.setDaemon(true);
                return t;
            });
            this.cleanupExecutor.scheduleAtFixedRate(
                this::cleanupStaleRetries,
                CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            );
        } else {
            this.cleanupExecutor = null;
        }
    }
    
    boolean shouldRetry(T item, Throwable error) {
        // Cache maxRetries to avoid repeated method calls (optimization)
        int maxRetries = config.getMaxRetries();
        if (maxRetries <= 0) {
            return false;
        }
        
        if (!config.getRetryableErrorPredicate().test(error)) {
            return false;
        }
        
        AtomicInteger retryCount = retryCounts.get(item);
        return retryCount == null || retryCount.get() < maxRetries;
    }
    
    void scheduleRetry(T item, Throwable error, CompletableFuture<BatchResult<T>> originalFuture) {
        AtomicInteger retryCount = retryCounts.computeIfAbsent(item, k -> new AtomicInteger(0));
        int currentRetries = retryCount.incrementAndGet();
        metrics.recordRequestRetried();
        
        if (debugMode) {
            logger.debug("Scheduling retry {} for item: {}, error: {}", 
                currentRetries, item, error.getClass().getSimpleName());
        }
        
        Runnable retryTask = () -> {
            try {
                if (isClosedSupplier.get()) {
                    retryCounts.remove(item);
                    originalFuture.complete(new BatchResult<>(
                        List.of(),
                        List.of(new FailureEvent<>(item, new IllegalStateException("Batcher is closed"))))
                    );
                    return;
                }
                
                CompletableFuture<BatchResult<T>> retryFuture = submitFunction.apply(item);
                retryFuture.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        retryCounts.remove(item);
                        originalFuture.completeExceptionally(throwable);
                    } else {
                        originalFuture.complete(result);
                    }
                });
            } catch (IllegalStateException e) {
                if (debugMode) {
                    logger.debug("Cannot retry item {} - batcher is closed", item);
                }
                retryCounts.remove(item);
                originalFuture.complete(new BatchResult<>(
                    List.of(),
                    List.of(new FailureEvent<>(item, e)))
                );
            }
        };
        
        if (config.getRetryDelay().isZero()) {
            executor.submit(retryTask);
        } else {
            executor.submit(() -> {
                try {
                    Thread.sleep(config.getRetryDelay().toMillis());
                    retryTask.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    retryCounts.remove(item);
                    originalFuture.complete(new BatchResult<>(
                        List.of(),
                        List.of(new FailureEvent<>(item, e)))
                    );
                }
            });
        }
    }
    
    /**
     * Attempts to retry the item if retryable, otherwise records failure metric and completes the future.
     * This is a convenience method that combines shouldRetry(), scheduleRetry(), and failure handling.
     * 
     * @param item the item to retry
     * @param error the error that occurred
     * @param future the future to complete if retry is not possible
     * @return true if retry was scheduled, false if the future was completed with failure
     */
    boolean tryRetryOrFail(T item, Throwable error, CompletableFuture<BatchResult<T>> future) {
        if (shouldRetry(item, error)) {
            scheduleRetry(item, error, future);
            return true;
        } else {
            metrics.recordRequestFailed();
            future.complete(new BatchResult<>(
                List.of(),
                List.of(new FailureEvent<>(item, error))
            ));
            return false;
        }
    }
    
    void clearRetryCount(T item) {
        retryCounts.remove(item);
    }
    
    public void clearAll() {
        retryCounts.clear();
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanupExecutor.shutdownNow();
            }
        }
    }
    
    /**
     * Cleans up stale retry count entries.
     * 
     * <p>This method removes entries for items that have reached max retries
     * to prevent the map from growing unbounded in scenarios with many unique
     * items being retried.
     */
    private void cleanupStaleRetries() {
        int maxRetries = config.getMaxRetries();
        if (maxRetries <= 0) {
            return;
        }

        if (isClosedSupplier.get()) {
            // Batcher is closed, no need to clean up
            return;
        }
        
        AtomicInteger removedCount = new AtomicInteger(0);
        
        // Remove entries that have reached max retries
        retryCounts.entrySet().removeIf(entry -> {
            AtomicInteger count = entry.getValue();
            if (count.get() >= maxRetries) {
                removedCount.incrementAndGet();
                return true;
            }
            return false;
        });
        
        int removed = removedCount.get();
        if (debugMode && removed > 0) {
            logger.debug("Cleaned up {} stale retry count entries", removed);
        }
    }
}

