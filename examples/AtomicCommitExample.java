package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            futures.add(batcher.submit("item-1"));
            futures.add(batcher.submit("item-2"));
            futures.add(batcher.submit("fail-item")); // This will cause all to fail
            futures.add(batcher.submit("item-4"));
            futures.add(batcher.submit("item-5"));
            
            for (CompletableFuture<BatchResult<String>> future : futures) {
                BatchResult<String> result = future.get();
                System.out.println("Result: " + 
                    (result.isAllSuccess() ? "SUCCESS" : "FAILED") + 
                    " (Successes: " + result.getSuccesses().size() + 
                    ", Failures: " + result.getFailures().size() + ")");
            }
        }
    }
}

