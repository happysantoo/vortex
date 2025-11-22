package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
                        successes.add(new SuccessEvent<>(item));
                    } else if (item.contains("fail")) {
                        failures.add(new FailureEvent<>(item, new RuntimeException("Failed")));
                    } else {
                        successes.add(new SuccessEvent<>(item));
                    }
                }
                
                return new BatchResult<>(successes, failures);
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
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            futures.add(batcher.submit("normal-item"));
            futures.add(batcher.submit("retry-item")); // Will be replayed by backend decision
            futures.add(batcher.submit("fail-item"));
            futures.add(batcher.submit("normal-item-2"));
            futures.add(batcher.submit("retry-item-2"));
            
            Thread.sleep(1000); // Wait for processing
            
            System.out.println("Requests replayed: " + 
                batcher.getMeterRegistry().counter("vortex.requests.replayed").count());
        }
    }
}

