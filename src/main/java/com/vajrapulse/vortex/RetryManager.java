package com.vajrapulse.vortex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Manages retry logic for failed items.
 */
class RetryManager<T> {
    private static final Logger logger = LoggerFactory.getLogger(RetryManager.class);
    
    private final BatcherConfig config;
    private final ExecutorService executor;
    private final Function<T, CompletableFuture<BatchResult<T>>> submitFunction;
    private final java.util.function.Supplier<Boolean> isClosedSupplier;
    private final ConcurrentHashMap<T, AtomicInteger> retryCounts = new ConcurrentHashMap<>();
    
    RetryManager(BatcherConfig config, ExecutorService executor, 
                 Function<T, CompletableFuture<BatchResult<T>>> submitFunction,
                 java.util.function.Supplier<Boolean> isClosedSupplier) {
        this.config = config;
        this.executor = executor;
        this.submitFunction = submitFunction;
        this.isClosedSupplier = isClosedSupplier;
    }
    
    boolean shouldRetry(T item, Throwable error) {
        if (config.getMaxRetries() <= 0) {
            return false;
        }
        
        if (!config.getRetryableErrorPredicate().test(error)) {
            return false;
        }
        
        AtomicInteger retryCount = retryCounts.get(item);
        if (retryCount == null) {
            return true;
        }
        
        return retryCount.get() < config.getMaxRetries();
    }
    
    void scheduleRetry(T item, Throwable error, CompletableFuture<BatchResult<T>> originalFuture) {
        AtomicInteger retryCount = retryCounts.computeIfAbsent(item, k -> new AtomicInteger(0));
        int currentRetries = retryCount.incrementAndGet();
        
        if (config.isDebugMode()) {
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
                if (config.isDebugMode()) {
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
    
    void clearRetryCount(T item) {
        retryCounts.remove(item);
    }
    
    void clearAll() {
        retryCounts.clear();
    }
}

