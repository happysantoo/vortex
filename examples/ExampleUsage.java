package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Example usage of the MicroBatcher library.
 */
public class ExampleUsage {
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // Create a simple backend that simulates processing
        // Backend can be blocking - it will run on a virtual thread
        Backend<String> backend = batch -> {
            System.out.println("Processing batch of size: " + batch.size());
            
            // Simulate some processing (this can be blocking I/O)
            // Since it runs on a virtual thread, blocking is efficient
            try {
                Thread.sleep(10); // Simulate network/database call
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
            
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("error")) {
                    failures.add(new com.vajrapulse.vortex.results.FailureEvent<>(item, new RuntimeException("Simulated error")));
                } else {
                    successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                }
            }
            
            return new com.vajrapulse.vortex.results.BatchResult<>(successes, failures);
        };
        
        // Configure the batcher
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(200))
            .atomicCommit(false)
            .maxQueueSize(20)  // Custom queue size (default would be 2x batchSize = 10)
            .build();
        
        // Create the micro-batcher
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            
            // OPTION 1: Use submit(item, callback) (RECOMMENDED - Cleanest)
            // No need to track futures - callback handles each item's result
            // 
            // ERROR HANDLING:
            // - Queue size is limited (default: 2x batch size, or custom maxQueueSize)
            // - If queue is full, submit() returns ItemResult.Failure immediately with ItemRejectedException
            // - Always check ItemResult to detect rejections!
            ItemCallback<String> callback = result -> {
                // Callback fires when item is processed
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("Request " + success.getItem() + " succeeded");
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("Request " + failure.getItem() + " failed: " + failure.error().getMessage());
                }
            };
            
            for (int requestIndex = 0; requestIndex < 15; requestIndex++) {
                final int requestId = requestIndex;
                ItemResult<String> result = batcher.submit("Request-" + requestId, callback);
                
                // Check immediate rejection (queue full, etc.)
                if (result instanceof ItemResult.Failure<String> failure) {
                    if (failure.error() instanceof ItemRejectedException) {
                        System.err.println("⚠ Request " + requestId + " REJECTED: " + failure.error().getMessage());
                        // Options: retry, log, send to dead letter queue, or fail fast
                    } else {
                        System.err.println("⚠ Request " + requestId + " ERROR: " + failure.error().getMessage());
                    }
                }
            }
            
            Thread.sleep(500); // Wait for processing
            
            // Print metrics
            System.out.println("\n=== Metrics ===");
            System.out.println("Requests submitted: " + 
                batcher.getMeterRegistry().counter("vortex.requests.submitted").count());
            System.out.println("Batches dispatched: " + 
                batcher.getMeterRegistry().counter("vortex.batches.dispatched").count());
            System.out.println("Requests succeeded: " + 
                batcher.getMeterRegistry().counter("vortex.requests.succeeded").count());
            System.out.println("Requests failed: " + 
                batcher.getMeterRegistry().counter("vortex.requests.failed").count());
        }
    }
}
