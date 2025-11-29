package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Example showing how to handle backpressure when using submitWithCallback.
 * 
 * Current backpressure mechanisms:
 * 1. Queue size: Limited to 2x batch size (e.g., batchSize=5 → queue=10 items)
 * 2. Queue offer timeout: 100ms - if queue is full, waits up to 100ms then rejects
 * 3. Rejection: Returns RejectedExecutionException when queue is full
 * 4. Metrics: Queue depth is tracked via vortex.queue.depth gauge
 */
public class ExampleUsageWithBackpressure {
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        Backend<String> backend = batch -> {
            System.out.println("Processing batch of size: " + batch.size());
            try {
                Thread.sleep(50); // Simulate processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
            
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        // Configure queue size for backpressure control
        // Option 1: Use default (2x batchSize = 10 items max)
        // Option 2: Set custom maxQueueSize (shown here)
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .maxQueueSize(15)  // Custom queue size (default would be 2x batchSize = 10)
            .lingerTime(Duration.ofMillis(200))
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            
            // Track rejections
            AtomicInteger rejections = new AtomicInteger(0);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            
            List<CompletableFuture<Void>> callbacks = new ArrayList<>();
            
            // OPTION 1: Handle RejectedExecutionException in callback
            // When queue is full, the future completes exceptionally
            System.out.println("=== Submitting 20 items (queue max = 10) ===");
            for (int requestIndex = 0; requestIndex < 20; requestIndex++) {
                final int requestId = requestIndex;
                CompletableFuture<Void> callback = batcher.submitWithCallback(
                    "Request-" + requestId,
                    (item, result) -> {
                        if (result instanceof ItemResult.Success) {
                            successes.incrementAndGet();
                            System.out.println("✓ " + item + " succeeded");
                        } else {
                            failures.incrementAndGet();
                            System.out.println("✗ " + item + " failed");
                        }
                    }
                );
                
                // Handle rejection (queue full)
                callback.exceptionally(throwable -> {
                    if (throwable.getCause() instanceof RejectedExecutionException) {
                        rejections.incrementAndGet();
                        System.out.println("⚠ Request " + requestId + " REJECTED: Queue is full");
                        // Option: Retry, log, or send to dead letter queue
                        return null;
                    }
                    // Other exceptions
                    System.out.println("⚠ Request " + requestId + " ERROR: " + throwable.getMessage());
                    return null;
                });
                
                callbacks.add(callback);
                
                // Small delay to show backpressure in action
                Thread.sleep(5);
            }
            
            // Wait for all callbacks (including rejected ones)
            CompletableFuture.allOf(callbacks.toArray(new CompletableFuture[0])).get();
            
            System.out.println("\n=== Results ===");
            System.out.println("Successes: " + successes.get());
            System.out.println("Failures: " + failures.get());
            System.out.println("Rejections (queue full): " + rejections.get());
            
            // Monitor queue depth via metrics
            System.out.println("\n=== Queue Metrics ===");
            double queueDepth = batcher.getMeterRegistry()
                .gauge("vortex.queue.depth", 0.0);
            System.out.println("Current queue depth: " + queueDepth);
            System.out.println("Max queue size: " + config.getMaxQueueSize());
            
            // Print other metrics
            System.out.println("\n=== Other Metrics ===");
            System.out.println("Requests submitted: " + 
                batcher.getMeterRegistry().counter("vortex.requests.submitted").count());
            System.out.println("Batches dispatched: " + 
                batcher.getMeterRegistry().counter("vortex.batches.dispatched").count());
        }
    }
}

