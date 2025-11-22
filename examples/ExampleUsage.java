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
            .maxConcurrency(10)
            .build();
        
        // Create the micro-batcher
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            
            // Submit some requests
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            for (int i = 0; i < 15; i++) {
                final int idx = i;
                CompletableFuture<BatchResult<String>> future = batcher.submit("Request-" + idx);
                futures.add(future);
                
                future.thenAccept(result -> {
                    System.out.println("Request " + idx + " completed. " +
                        "Successes: " + result.getSuccesses().size() + ", " +
                        "Failures: " + result.getFailures().size());
                });
            }
            
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
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

