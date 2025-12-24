package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating atomic commit mode where all items fail if any fails.
 */
public class AtomicCommitExample {
    
    public static void main(String[] args) throws Exception {
        Backend<String> backend = batch -> {
            System.out.println("Processing batch: " + batch);
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("fail")) {
                    failures.add(new com.vajrapulse.vortex.results.FailureEvent<>(item, new RuntimeException("Item failed")));
                } else {
                    successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                }
            }
            
            return new com.vajrapulse.vortex.results.BatchResult<>(successes, failures);
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .atomicCommit(true) // All fail if any fails
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            // Reusable callback that extracts item name from result
            ItemCallback<String> callback = result -> {
                String itemName = result.getItem();
                String status = result instanceof ItemResult.Success<String> 
                    ? "SUCCESS" 
                    : "FAILED - " + ((ItemResult.Failure<String>) result).error().getMessage();
                System.out.println(itemName + ": " + status);
            };
            
            // Submit items - the fail-item will cause all items in the batch to fail due to atomic commit
            batcher.submit("item-1", callback);
            batcher.submit("item-2", callback);
            batcher.submit("fail-item", callback);
            batcher.submit("item-4", callback);
            batcher.submit("item-5", callback);
            
            Thread.sleep(500); // Wait for batch processing
        }
    }
}

