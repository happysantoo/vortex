package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating time-based batching (linger time).
 */
public class TimeBasedBatchingExample {
    
    public static void main(String[] args) throws Exception {
        Backend<String> backend = batch -> {
            System.out.println("Batch dispatched at " + System.currentTimeMillis() + 
                " with " + batch.size() + " items");
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        // Small batch size but longer linger time - batches will trigger on time
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(500)) // 500ms linger
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            // Submit items slowly - they'll batch by time, not size
            for (int i = 0; i < 5; i++) {
                batcher.submit("Item-" + i);
                Thread.sleep(100); // Submit every 100ms
            }
            
            Thread.sleep(1000); // Wait for time-based batch to trigger
        }
    }
}

