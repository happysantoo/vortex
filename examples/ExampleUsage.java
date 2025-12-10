package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
                    failures.add(new FailureEvent<>(item, new RuntimeException("Simulated error")));
                } else {
                    successes.add(new SuccessEvent<>(item));
                }
            }
            
            return new BatchResult<>(successes, failures);
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
            
            // OPTION 1: Use submitWithCallback (RECOMMENDED - Cleanest)
            // No need to track futures - callback handles each item's result
            // 
            // BACKPRESSURE HANDLING:
            // - Queue size is limited to 2x batch size (e.g., batchSize=5 → queue=10 items)
            // - If queue is full, submit() waits up to 100ms, then rejects with BackpressureException
            // - As of 0.0.8, all rejections (queue full, concurrent limit, backpressure) throw BackpressureException
            // - Always handle exceptions to detect backpressure!
            List<CompletableFuture<Void>> callbacks = new ArrayList<>();
            for (int requestIndex = 0; requestIndex < 15; requestIndex++) {
                final int requestId = requestIndex;
                CompletableFuture<Void> callback = batcher.submitWithCallback(
                    "Request-" + requestId,
                    (item, result) -> {
                        if (result instanceof ItemResult.Success) {
                            System.out.println("Request " + requestId + " succeeded: " + item);
                        } else if (result instanceof ItemResult.Failure) {
                            System.out.println("Request " + requestId + " failed: " + 
                                ((ItemResult.Failure<String>) result).error().getMessage());
                        }
                    }
                );
                
                // Handle backpressure: queue full, concurrent limit, or backpressure threshold
                callback.exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof com.vajrapulse.vortex.backpressure.BackpressureException) {
                        com.vajrapulse.vortex.backpressure.BackpressureException bpEx = 
                            (com.vajrapulse.vortex.backpressure.BackpressureException) cause;
                        System.err.println("⚠ Request " + requestId + " REJECTED: " + bpEx.getMessage() +
                            " (level: " + bpEx.getBackpressureLevel() + ", source: " + bpEx.getSourceName() + ")");
                        // Options: retry, log, send to dead letter queue, or fail fast
                    } else {
                        System.err.println("⚠ Request " + requestId + " ERROR: " + throwable.getMessage());
                    }
                    return null; // Complete exceptionally handled
                });
                
                callbacks.add(callback);
            }
            
            // Wait for all callbacks to complete
            CompletableFuture.allOf(callbacks.toArray(new CompletableFuture[0])).get();
            
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
