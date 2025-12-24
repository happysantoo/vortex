package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Demonstrates all three ways to submit items to MicroBatcher:
 * 
 * 1. submit(item) - Synchronous, returns ItemResult immediately
 * 2. submit(item, callback) - Synchronous return + async callback for result
 * 3. submitAsync(item) - Returns CompletableFuture for async processing
 * 
 * This example shows when to use each method and how to handle results.
 */
public class ThreeSubmissionMethodsExample {
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
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
            
            System.out.println("=== Method 1: submit(item) - Synchronous ===\n");
            demonstrateSubmitMethod(batcher);
            
            System.out.println("\n=== Method 2: submit(item, callback) - Callback-based ===\n");
            demonstrateSubmitWithCallback(batcher);
            
            System.out.println("\n=== Method 3: submitAsync(item) - CompletableFuture ===\n");
            demonstrateSubmitAsync(batcher);
            
            // Wait for all processing to complete
            Thread.sleep(500);
            System.out.println("\n=== All methods demonstrated ===\n");
        }
    }
    
    /**
     * Method 1: submit(item)
     * - Returns ItemResult immediately (synchronous)
     * - Use when: You only need to know if item was accepted/rejected immediately
     * - Don't need to know when processing completes
     */
    private static void demonstrateSubmitMethod(MicroBatcher<String> batcher) {
        System.out.println("Method 1: submit(item) - Returns ItemResult immediately");
        System.out.println("Use case: Fire-and-forget, only care about immediate acceptance\n");
        
        for (int i = 1; i <= 3; i++) {
            ItemResult<String> result = batcher.submit("item-" + i);
            
            if (result instanceof ItemResult.Success<String> success) {
                System.out.println("  ✓ Item accepted: " + success.getItem());
                // Item will be processed in batch later - we don't wait for it
            } else if (result instanceof ItemResult.Failure<String> failure) {
                System.out.println("  ✗ Item rejected: " + failure.getItem() + 
                    " - " + failure.error().getMessage());
            }
        }
    }
    
    /**
     * Method 2: submit(item, callback)
     * - Returns ItemResult immediately (synchronous)
     * - Callback fires later when item is processed
     * - Use when: You need immediate acceptance check AND want to know processing result
     */
    private static void demonstrateSubmitWithCallback(MicroBatcher<String> batcher) {
        System.out.println("Method 2: submit(item, callback) - Immediate return + async callback");
        System.out.println("Use case: Need immediate acceptance AND want processing result\n");
        
        for (int i = 1; i <= 3; i++) {
            final int itemNum = i;
            ItemResult<String> immediateResult = batcher.submit("item-" + i, result -> {
                // This callback fires when the item is processed (async)
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("  ✓ Callback: Item " + itemNum + " processed successfully: " + success.getItem());
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("  ✗ Callback: Item " + itemNum + " failed: " + failure.error().getMessage());
                }
            });
            
            // Immediate result tells us if item was accepted
            if (immediateResult instanceof ItemResult.Success<String>) {
                System.out.println("  → Item " + itemNum + " accepted (callback will fire later)");
            } else if (immediateResult instanceof ItemResult.Failure<String>) {
                System.out.println("  → Item " + itemNum + " rejected immediately");
            }
        }
    }
    
    /**
     * Method 3: submitAsync(item)
     * - Returns CompletableFuture immediately (never blocks)
     * - Future completes when item is processed
     * - Use when: You want to chain async operations, use CompletableFuture APIs
     */
    private static void demonstrateSubmitAsync(MicroBatcher<String> batcher) throws ExecutionException, InterruptedException {
        System.out.println("Method 3: submitAsync(item) - Returns CompletableFuture");
        System.out.println("Use case: Async processing, chaining operations, CompletableFuture.allOf()\n");
        
        List<CompletableFuture<ItemResult<String>>> futures = new ArrayList<>();
        
        for (int i = 1; i <= 3; i++) {
            final int itemNum = i;
            CompletableFuture<ItemResult<String>> future = batcher.submitAsync("item-" + i)
                .thenApply(result -> {
                    // Transform result
                    if (result instanceof ItemResult.Success<String> success) {
                        System.out.println("  ✓ Future: Item " + itemNum + " processed: " + success.getItem());
                    } else if (result instanceof ItemResult.Failure<String> failure) {
                        System.out.println("  ✗ Future: Item " + itemNum + " failed: " + failure.error().getMessage());
                    }
                    return result;
                })
                .exceptionally(throwable -> {
                    // Handle immediate rejection (queue full, etc.)
                    System.out.println("  ✗ Future: Item " + itemNum + " rejected: " + throwable.getMessage());
                    return null;
                });
            
            futures.add(future);
        }
        
        // IMPORTANT: Wait for all futures to complete before the method returns.
        // Without this, the main thread might finish and the JVM could exit before
        // the async processing completes (since the batcher uses background threads).
        // In production code, you might not need this if your application keeps running.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        System.out.println("  → All async items processed");
    }
}

