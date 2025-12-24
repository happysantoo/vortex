package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Basic usage example demonstrating simple batching with MicroBatcher.
 * 
 * This is the simplest example showing:
 * - Creating a backend
 * - Configuring the batcher
 * - Submitting items
 * - Basic error checking
 */
public class BasicUsageExample {
    
    public static void main(String[] args) throws Exception {
        // Create a simple backend that processes strings
        Backend<String> backend = batch -> {
            System.out.println("Processing batch of " + batch.size() + " items");
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        // Configure the batcher
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)  // Process items in batches of 5
            .lingerTime(Duration.ofMillis(100))  // Wait up to 100ms to fill a batch
            .build();
        
        // Use try-with-resources to ensure proper cleanup
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            // Submit items - they will be batched automatically
            for (int i = 1; i <= 12; i++) {
                ItemResult<String> result = batcher.submit("Item-" + i);
                
                // Check if item was accepted or rejected
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("Accepted: " + success.getItem());
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("Rejected: " + failure.getItem() + " - " + failure.error().getMessage());
                }
            }
            
            // Wait a bit for batches to process
            Thread.sleep(500);
        }
        // batcher.close() is called automatically by try-with-resources
    }
}
