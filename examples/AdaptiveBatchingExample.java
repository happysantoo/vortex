package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating adaptive batching based on metrics.
 * 
 * This example shows how to use MetricsProvider to adjust batch size
 * dynamically based on failure rate and queue depth.
 */
public class AdaptiveBatchingExample {
    
    public static void main(String[] args) throws Exception {
        // Simulate a backend with variable failure rate
        Backend<String> backend = batch -> {
            // Simulate failures for items starting with "fail"
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.startsWith("fail")) {
                    failures.add(new FailureEvent<>(item, new RuntimeException("Simulated failure")));
                } else {
                    successes.add(new SuccessEvent<>(item));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10) // Initial batch size
            .lingerTime(Duration.ofMillis(100))
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)) {
            MetricsProvider metrics = batcher.getMetricsProvider();
            
            // Adaptive batching loop
            for (int i = 0; i < 100; i++) {
                // Check metrics and adjust batch size
                double failureRate = metrics.getFailureRate();
                int queueDepth = metrics.getQueueDepth();
                
                if (i > 0 && i % 10 == 0) { // Adjust every 10 items
                    if (failureRate > 0.2) {
                        // High failure rate - reduce batch size
                        int newSize = Math.max(2, batcher.getCurrentBatchSize() - 2);
                        batcher.updateBatchSize(newSize);
                        System.out.printf("High failure rate (%.2f%%) - Reducing batch size to %d%n", 
                            failureRate * 100, newSize);
                    } else if (failureRate < 0.05 && queueDepth < 5) {
                        // Low failure rate and low queue - increase batch size
                        int newSize = Math.min(20, batcher.getCurrentBatchSize() + 2);
                        batcher.updateBatchSize(newSize);
                        System.out.printf("Low failure rate (%.2f%%) - Increasing batch size to %d%n", 
                            failureRate * 100, newSize);
                    }
                }
                
                // Submit items (mix of success and failure)
                String item = (i % 5 == 0) ? "fail-" + i : "success-" + i;
                batcher.submit(item);
                
                Thread.sleep(10);
            }
            
            // Wait for processing
            Thread.sleep(500);
            
            // Print final metrics
            System.out.println("\n=== Final Metrics ===");
            System.out.printf("Total submitted: %d%n", metrics.getTotalSubmitted());
            System.out.printf("Total succeeded: %d%n", metrics.getTotalSucceeded());
            System.out.printf("Total failed: %d%n", metrics.getTotalFailed());
            System.out.printf("Failure rate: %.2f%%%n", metrics.getFailureRate() * 100);
            System.out.printf("Success rate: %.2f%%%n", metrics.getSuccessRate() * 100);
            System.out.printf("Queue depth: %d%n", metrics.getQueueDepth());
            System.out.printf("Batches dispatched: %d%n", metrics.getTotalBatchesDispatched());
            System.out.printf("Avg dispatch latency: %.2f ms%n", metrics.getAverageDispatchLatency());
        }
    }
}

