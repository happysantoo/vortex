package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            futures.add(batcher.submit("success-1"));
            futures.add(batcher.submit("success-2"));
            futures.add(batcher.submit("fail-1")); // Mixed batch
            futures.add(batcher.submit("success-3"));
            futures.add(batcher.submit("fail-2"));
            
            Thread.sleep(1000); // Wait for processing and replay
            
            System.out.println("\nMetrics:");
            System.out.println("Requests replayed: " + 
                batcher.getMeterRegistry().counter("vortex.requests.replayed").count());
        }
    }
}

