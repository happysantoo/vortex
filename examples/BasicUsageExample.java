package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Basic usage example demonstrating simple batching.
 */
public class BasicUsageExample {
    
    public static void main(String[] args) throws Exception {
        // Simple backend that processes strings
        Backend<String> backend = batch -> {
            System.out.println("Processing batch of " + batch.size() + " items");
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            for (int i = 0; i < 12; i++) {
                CompletableFuture<BatchResult<String>> future = batcher.submit("Item-" + i);
                future.thenAccept(result -> 
                    System.out.println("Completed: " + result.getSuccesses().size() + " successes")
                );
            }
            Thread.sleep(500); // Wait for processing
        }
    }
}

