package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Example demonstrating custom backend with shouldReplaySuccesses override.
 */
public class CustomBackendReplayExample {
    
    public static void main(String[] args) throws Exception {
        // Custom backend that decides replay behavior
        Backend<String> customBackend = new Backend<String>() {
            @Override
            public BatchResult<String> dispatch(List<String> batch) throws Exception {
                System.out.println("Processing batch: " + batch);
                List<SuccessEvent<String>> successes = new ArrayList<>();
                List<FailureEvent<String>> failures = new ArrayList<>();
                
                for (String item : batch) {
                    if (item.contains("retry")) {
                        // Items with "retry" should be replayed
                        successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                    } else if (item.contains("fail")) {
                        failures.add(new com.vajrapulse.vortex.results.FailureEvent<>(item, new RuntimeException("Failed")));
                    } else {
                        successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                    }
                }
                
                return new com.vajrapulse.vortex.results.BatchResult<>(successes, failures);
            }
            
            @Override
            public boolean shouldReplaySuccesses(BatchResult<String> result) {
                // Custom logic: replay if we have successes with "retry" in them
                return result.getSuccesses().stream()
                    .anyMatch(s -> s.getData().contains("retry"));
            }
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .autoReplaySuccesses(false) // Backend will decide
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(customBackend, config)) {
            // Reusable callback that extracts item name from result
            ItemCallback<String> callback = result -> {
                String itemName = result.getItem();
                String status = result instanceof ItemResult.Success<String> ? "SUCCESS" : "FAILED";
                System.out.println(itemName + ": " + status);
            };
            
            // Submit items - retry-item will be replayed by backend decision
            batcher.submit("normal-item", callback);
            batcher.submit("retry-item", callback);
            batcher.submit("fail-item", callback);
            batcher.submit("normal-item-2", callback);
            batcher.submit("retry-item-2", callback);
            
            Thread.sleep(1000); // Wait for processing and replay
            
            System.out.println("\nRequests replayed: " + 
                batcher.getMeterRegistry().counter("vortex.requests.replayed").count());
        }
    }
}

