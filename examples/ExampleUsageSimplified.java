package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified examples showing different ways to use MicroBatcher.
 * These examples demonstrate various patterns for handling results.
 */
public class ExampleUsageSimplified {
    
    public static void main(String[] args) throws Exception {
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
            .lingerTime(Duration.ofMillis(200))
            .build();
        
        // ============================================
        // OPTION 1: Fire-and-Forget (Simplest)
        // ============================================
        // Just submit items without tracking - good for logging, metrics, etc.
        System.out.println("\n=== Option 1: Fire-and-Forget ===");
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            for (int itemIndex = 0; itemIndex < 10; itemIndex++) {
                batcher.submit("Item-" + itemIndex);
                // No tracking needed - items will be processed asynchronously
            }
            Thread.sleep(500); // Give time for processing
        }
        
        // ============================================
        // OPTION 2: Callback-Based (Recommended)
        // ============================================
        // Use submitWithCallback - cleanest API, handles per-item results
        System.out.println("\n=== Option 2: Callback-Based ===");
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            List<CompletableFuture<Void>> callbacks = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < 10; itemIndex++) {
                final int itemId = itemIndex;
                CompletableFuture<Void> callback = batcher.submitWithCallback(
                    "Item-" + itemId,
                    (item, result) -> {
                        if (result instanceof ItemResult.Success) {
                            System.out.println("✓ " + item + " succeeded");
                        } else {
                            System.out.println("✗ " + item + " failed");
                        }
                    }
                );
                // Handle backpressure (queue full)
                callback.exceptionally(throwable -> {
                    if (throwable.getCause() instanceof java.util.concurrent.RejectedExecutionException) {
                        System.err.println("⚠ " + itemId + " rejected: Queue full");
                    }
                    return null;
                });
                callbacks.add(callback);
            }
            // Wait for all callbacks to complete
            CompletableFuture.allOf(callbacks.toArray(new CompletableFuture[0])).get();
        }
        
        // ============================================
        // OPTION 3: Batch Wait (If you need all results)
        // ============================================
        // Submit all items, then wait for all at once
        System.out.println("\n=== Option 3: Batch Wait ===");
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < 10; itemIndex++) {
                futures.add(batcher.submit("Item-" + itemIndex));
            }
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            System.out.println("All " + futures.size() + " items processed");
        }
        
        // ============================================
        // OPTION 4: Stream-Based (Functional Style)
        // ============================================
        // Use Java streams for a more functional approach
        System.out.println("\n=== Option 4: Stream-Based ===");
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            List<CompletableFuture<Void>> results = java.util.stream.IntStream.range(0, 10)
                .mapToObj(itemIndex -> batcher.submitWithCallback(
                    "Item-" + itemIndex,
                    (item, result) -> System.out.println("Processed: " + item)
                ))
                .toList();
            
            CompletableFuture.allOf(results.toArray(new CompletableFuture[0])).get();
        }
    }
}

