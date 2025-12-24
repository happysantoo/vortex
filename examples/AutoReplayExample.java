package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating auto-replay of successful items when batch has mixed results.
 */
public class AutoReplayExample {
    
    public static void main(String[] args) throws Exception {
        Backend<String> backend = batch -> {
            System.out.println("Backend processing: " + batch);
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("fail")) {
                    failures.add(new com.vajrapulse.vortex.results.FailureEvent<>(item, new RuntimeException("Failed")));
                } else {
                    successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                }
            }
            
            return new com.vajrapulse.vortex.results.BatchResult<>(successes, failures);
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .autoReplaySuccesses(true) // Replay successful items
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            // Reusable callback that extracts item name from result
            ItemCallback<String> callback = result -> {
                String itemName = result.getItem();
                String status = result instanceof ItemResult.Success<String> ? "SUCCESS" : "FAILED";
                System.out.println(itemName + " result: " + status);
            };
            
            // Submit items - mixed batch with successful items will be replayed
            batcher.submit("success-1", callback);
            batcher.submit("success-2", callback);
            batcher.submit("fail-1", callback);
            batcher.submit("success-3", callback);
            batcher.submit("fail-2", callback);
            
            Thread.sleep(1000); // Wait for processing and replay
            
            System.out.println("\nMetrics:");
            System.out.println("Requests replayed: " + 
                batcher.getMeterRegistry().counter("vortex.requests.replayed").count());
        }
    }
}

