package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.vajrapulse.vortex.backpressure.BackpressureException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates backpressure handling when the batch queue reaches its maximum capacity.
 * 
 * This example shows:
 * 1. How to configure maxQueueSize to control backpressure
 * 2. How to detect and handle BackpressureException when queue is full, concurrent limit reached, or backpressure threshold exceeded
 * 3. Strategies for handling backpressure (retry, circuit breaker, rate limiting)
 * 4. Monitoring queue depth to prevent backpressure
 * 
 * Note: As of 0.0.8, all rejections throw BackpressureException for unified exception handling.
 */
public class BackpressureExample {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Backpressure Handling Example ===\n");
        
        // Example 1: Basic backpressure detection
        basicBackpressureDetection();
        
        // Example 2: Proactive monitoring to prevent backpressure
        proactiveMonitoring();
        
        // Example 3: Retry strategy with exponential backoff
        retryStrategyWithBackoff();
        
        // Example 4: Circuit breaker pattern
        circuitBreakerPattern();
        
        // Example 5: Rate limiting to prevent backpressure
        rateLimitingStrategy();
    }
    
    /**
     * Example 1: Basic backpressure detection and handling
     */
    private static void basicBackpressureDetection() throws Exception {
        System.out.println("--- Example 1: Basic Backpressure Detection ---");
        
        // Create a slow backend to simulate processing delay
        Backend<String> slowBackend = batch -> {
            Thread.sleep(100); // Simulate slow processing
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, new ArrayList<>());
        };
        
        // Configure with small queue size to trigger backpressure quickly
        BatcherConfig config = BatcherConfig.builder()
                .batchSize(5)
                .lingerTime(Duration.ofMillis(100))
                .maxQueueSize(10) // Small queue - will fill up quickly
                .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(slowBackend, config)) {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectionCount = new AtomicInteger(0);
            
            // Submit more items than the queue can handle
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < 50; itemIndex++) {
                final int itemId = itemIndex;
                CompletableFuture<BatchResult<String>> future = batcher.submit("item-" + itemId);
                
                future.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        if (throwable instanceof BackpressureException) {
                            rejectionCount.incrementAndGet();
                            System.out.println("  ❌ Item " + itemId + " rejected: Queue is full");
                        } else {
                            System.out.println("  ⚠️  Item " + itemId + " failed: " + throwable.getMessage());
                        }
                    } else {
                        successCount.incrementAndGet();
                    }
                });
                
                futures.add(future);
            }
            
            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            System.out.println("\n  Results:");
            System.out.println("    ✅ Successfully processed: " + successCount.get());
            System.out.println("    ❌ Rejected due to backpressure: " + rejectionCount.get());
        }
        
        System.out.println();
    }
    
    /**
     * Example 2: Proactive monitoring to prevent backpressure
     */
    private static void proactiveMonitoring() throws Exception {
        System.out.println("--- Example 2: Proactive Monitoring ---");
        
        Backend<String> backend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, new ArrayList<>());
        };
        
        BatcherConfig config = BatcherConfig.builder()
                .batchSize(10)
                .lingerTime(Duration.ofMillis(50))
                .maxQueueSize(20)
                .build();
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)) {
            MetricsProvider metrics = batcher.getMetricsProvider();
            
            // Simulate high load with monitoring
            for (int requestIndex = 0; requestIndex < 100; requestIndex++) {
                // Check queue depth before submitting
                int queueDepth = metrics.getQueueDepth();
                int maxQueueSize = config.getMaxQueueSize();
                double queueUtilization = (double) queueDepth / maxQueueSize;
                
                if (queueUtilization > 0.8) {
                    // Queue is 80% full - slow down submissions
                    System.out.println("  ⚠️  Queue utilization: " + String.format("%.1f%%", queueUtilization * 100) + 
                                     " - Pausing submissions");
                    Thread.sleep(50); // Pause to let queue drain
                } else if (queueUtilization > 0.5) {
                    // Queue is 50% full - reduce submission rate
                    Thread.sleep(10);
                }
                
                batcher.submit("item-" + requestIndex)
                    .whenComplete((result, throwable) -> {
                        if (throwable instanceof BackpressureException) {
                            System.out.println("  ❌ Rejection occurred despite monitoring!");
                        }
                    });
            }
            
            // Wait a bit for processing
            Thread.sleep(1000);
            
            System.out.println("\n  Final Metrics:");
            System.out.println("    Queue Depth: " + metrics.getQueueDepth());
            System.out.println("    Total Submitted: " + metrics.getTotalSubmitted());
            System.out.println("    Total Succeeded: " + metrics.getTotalSucceeded());
            System.out.println("    Total Failed: " + metrics.getTotalFailed());
        }
        
        System.out.println();
    }
    
    /**
     * Example 3: Retry strategy with exponential backoff
     */
    private static void retryStrategyWithBackoff() throws Exception {
        System.out.println("--- Example 3: Retry with Exponential Backoff ---");
        
        Backend<String> slowBackend = batch -> {
            Thread.sleep(200); // Slow processing
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, new ArrayList<>());
        };
        
        BatcherConfig config = BatcherConfig.builder()
                .batchSize(5)
                .lingerTime(Duration.ofMillis(100))
                .maxQueueSize(10)
                .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(slowBackend, config)) {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger retryCount = new AtomicInteger(0);
            
            // Submit with retry logic
            for (int itemIndex = 0; itemIndex < 30; itemIndex++) {
                final int itemId = itemIndex;
                submitWithRetry(batcher, "item-" + itemId, 0, successCount, retryCount);
            }
            
            // Wait for processing
            Thread.sleep(2000);
            
            System.out.println("\n  Results:");
            System.out.println("    ✅ Successfully submitted: " + successCount.get());
            System.out.println("    🔄 Retries attempted: " + retryCount.get());
        }
        
        System.out.println();
    }
    
    /**
     * Helper method for retry with exponential backoff
     */
    private static void submitWithRetry(
            MicroBatcher<String> batcher,
            String item,
            int attempt,
            AtomicInteger successCount,
            AtomicInteger retryCount) {
        
        CompletableFuture<BatchResult<String>> future = batcher.submit(item);
        
        future.whenComplete((result, throwable) -> {
            if (throwable instanceof BackpressureException && attempt < 3) {
                // Exponential backoff: 50ms, 100ms, 200ms
                long backoffMs = 50L * (1L << attempt);
                retryCount.incrementAndGet();
                
                System.out.println("  🔄 Retry attempt " + (attempt + 1) + " for " + item + 
                                 " after " + backoffMs + "ms");
                
                // Schedule retry
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(backoffMs);
                        submitWithRetry(batcher, item, attempt + 1, successCount, retryCount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } else if (throwable == null) {
                successCount.incrementAndGet();
            } else {
                System.out.println("  ❌ Failed after " + (attempt + 1) + " attempts: " + 
                                 throwable.getMessage());
            }
        });
    }
    
    /**
     * Example 4: Circuit breaker pattern
     */
    private static void circuitBreakerPattern() throws Exception {
        System.out.println("--- Example 4: Circuit Breaker Pattern ---");
        
        Backend<String> backend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, new ArrayList<>());
        };
        
        BatcherConfig config = BatcherConfig.builder()
                .batchSize(5)
                .lingerTime(Duration.ofMillis(100))
                .maxQueueSize(10)
                .build();
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)) {
            MetricsProvider metrics = batcher.getMetricsProvider();
            
            CircuitBreaker circuitBreaker = new CircuitBreaker(0.3, 5); // 30% threshold, 5 consecutive failures
            
            for (int requestIndex = 0; requestIndex < 50; requestIndex++) {
                // Check circuit breaker state
                if (circuitBreaker.isOpen()) {
                    System.out.println("  🔴 Circuit breaker OPEN - Rejecting submissions");
                    Thread.sleep(100); // Wait before checking again
                    continue;
                }
                
                CompletableFuture<BatchResult<String>> future = batcher.submit("item-" + requestIndex);
                
                future.whenComplete((result, throwable) -> {
                    if (throwable instanceof BackpressureException) {
                        circuitBreaker.recordFailure();
                    } else if (throwable == null) {
                        circuitBreaker.recordSuccess();
                    }
                });
            }
            
            Thread.sleep(1000);
            
            System.out.println("\n  Circuit Breaker State: " + 
                             (circuitBreaker.isOpen() ? "OPEN" : "CLOSED"));
            System.out.println("    Failure Rate: " + String.format("%.1f%%", 
                             metrics.getFailureRate() * 100));
        }
        
        System.out.println();
    }
    
    /**
     * Example 5: Rate limiting to prevent backpressure
     */
    private static void rateLimitingStrategy() throws Exception {
        System.out.println("--- Example 5: Rate Limiting Strategy ---");
        
        Backend<String> backend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, new ArrayList<>());
        };
        
        BatcherConfig config = BatcherConfig.builder()
                .batchSize(10)
                .lingerTime(Duration.ofMillis(50))
                .maxQueueSize(20)
                .build();
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)) {
            MetricsProvider metrics = batcher.getMetricsProvider();
            
            // Rate limiter: max 100 items per second
            RateLimiter rateLimiter = new RateLimiter(100);
            AtomicInteger submitted = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);
            
            long startTime = System.currentTimeMillis();
            long duration = 2000; // Run for 2 seconds
            
            while (System.currentTimeMillis() - startTime < duration) {
                if (rateLimiter.tryAcquire()) {
                    batcher.submit("item-" + submitted.getAndIncrement())
                        .whenComplete((result, throwable) -> {
                            if (throwable instanceof BackpressureException) {
                                rejected.incrementAndGet();
                            }
                        });
                } else {
                    // Rate limit exceeded - wait
                    Thread.sleep(10);
                }
            }
            
            Thread.sleep(500); // Wait for processing
            
            System.out.println("\n  Results:");
            System.out.println("    ✅ Submitted: " + submitted.get());
            System.out.println("    ❌ Rejected: " + rejected.get());
            System.out.println("    📊 Queue Depth: " + metrics.getQueueDepth());
            System.out.println("    📈 Success Rate: " + String.format("%.1f%%", 
                             metrics.getSuccessRate() * 100));
        }
        
        System.out.println();
    }
    
    /**
     * Simple circuit breaker implementation
     */
    static class CircuitBreaker {
        private final double failureThreshold;
        private final int consecutiveFailureThreshold;
        private int consecutiveFailures = 0;
        private boolean open = false;
        
        CircuitBreaker(double failureThreshold, int consecutiveFailureThreshold) {
            this.failureThreshold = failureThreshold;
            this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        }
        
        void recordFailure() {
            consecutiveFailures++;
            if (consecutiveFailures >= consecutiveFailureThreshold) {
                open = true;
            }
        }
        
        void recordSuccess() {
            consecutiveFailures = 0;
            open = false;
        }
        
        boolean isOpen() {
            return open;
        }
    }
    
    /**
     * Simple rate limiter implementation (token bucket)
     */
    static class RateLimiter {
        private final long permitsPerSecond;
        private long lastRefillTime;
        private double availablePermits;
        
        RateLimiter(long permitsPerSecond) {
            this.permitsPerSecond = permitsPerSecond;
            this.lastRefillTime = System.nanoTime();
            this.availablePermits = permitsPerSecond;
        }
        
        synchronized boolean tryAcquire() {
            refill();
            if (availablePermits >= 1.0) {
                availablePermits -= 1.0;
                return true;
            }
            return false;
        }
        
        private void refill() {
            long now = System.nanoTime();
            long elapsed = now - lastRefillTime;
            double permitsToAdd = (elapsed / 1_000_000_000.0) * permitsPerSecond;
            availablePermits = Math.min(permitsPerSecond, availablePermits + permitsToAdd);
            lastRefillTime = now;
        }
    }
}

