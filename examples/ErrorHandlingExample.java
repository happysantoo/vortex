package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Comprehensive error handling examples for MicroBatcher:
 * 
 * 1. Queue Full Handling - Detecting and handling ItemRejectedException
 * 2. Backend Error Handling - Processing failures from backend
 * 3. Retry Configuration - Setting up automatic retries
 * 
 * This example demonstrates best practices for error handling in production code.
 */
public class ErrorHandlingExample {
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        System.out.println("=== Error Handling Examples ===\n");
        
        demonstrateQueueFullHandling();
        demonstrateBackendErrorHandling();
        demonstrateRetryConfiguration();
        
        System.out.println("\n=== All error handling scenarios demonstrated ===");
    }
    
    /**
     * Example 1: Queue Full Handling
     * Shows how to detect and handle ItemRejectedException when queue is full.
     */
    private static void demonstrateQueueFullHandling() throws InterruptedException {
        System.out.println("=== Example 1: Queue Full Handling ===\n");
        
        Backend<String> backend = batch -> {
            // Slow backend to fill up queue
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        // Small queue to trigger rejections
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(500))
            .maxQueueSize(3)  // Very small queue
            .queueRejectionThreshold(0.8)  // Reject at 80% capacity
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            
            // Method 1: Handling rejection with submit()
            System.out.println("1. Handling rejection with submit():");
            for (int i = 1; i <= 5; i++) {
                ItemResult<String> result = batcher.submit("item-" + i);
                if (result instanceof ItemResult.Failure<String> failure) {
                    if (failure.error() instanceof ItemRejectedException) {
                        System.out.println("  ✗ Item " + i + " rejected: " + failure.error().getMessage());
                    }
                } else {
                    System.out.println("  ✓ Item " + i + " accepted");
                }
            }
            
            Thread.sleep(100);
            
            // Method 2: Handling rejection with submitAsync()
            System.out.println("\n2. Handling rejection with submitAsync():");
            for (int i = 6; i <= 10; i++) {
                final int itemNum = i;
                batcher.submitAsync("item-" + i)
                    .thenAccept(result -> {
                        if (result instanceof ItemResult.Success<String>) {
                            System.out.println("  ✓ Item " + itemNum + " processed successfully");
                        } else if (result instanceof ItemResult.Failure<String> failure) {
                            System.out.println("  ✗ Item " + itemNum + " failed: " + failure.error().getMessage());
                        }
                    })
                    .exceptionally(throwable -> {
                        if (throwable.getCause() instanceof ItemRejectedException) {
                            System.out.println("  ✗ Item " + itemNum + " rejected: " + throwable.getCause().getMessage());
                        } else {
                            System.out.println("  ✗ Item " + itemNum + " error: " + throwable.getMessage());
                        }
                        return null;
                    });
            }
            
            Thread.sleep(500);
        }
    }
    
    /**
     * Example 2: Backend Error Handling
     * Shows how to handle errors from the backend (processing failures).
     */
    private static void demonstrateBackendErrorHandling() throws InterruptedException {
        System.out.println("\n=== Example 2: Backend Error Handling ===\n");
        
        // Backend that fails for certain items
        Backend<String> backend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("error")) {
                    failures.add(new FailureEvent<>(item, new RuntimeException("Backend processing failed for: " + item)));
                } else {
                    successes.add(new SuccessEvent<>(item));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            
            // Method 1: Handling backend errors with callback
            System.out.println("1. Handling backend errors with callback:");
            ItemCallback<String> errorCallback = result -> {
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("  ✓ Success: " + success.getItem());
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("  ✗ Failure: " + failure.getItem() + " - " + failure.error().getMessage());
                }
            };
            
            batcher.submit("item-ok", errorCallback);
            batcher.submit("item-error", errorCallback);
            
            // Method 2: Handling backend errors with submitAsync()
            System.out.println("\n2. Handling backend errors with submitAsync():");
            java.util.function.Consumer<ItemResult<String>> asyncErrorHandler = result -> {
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("  ✓ Success: " + success.getItem());
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("  ✗ Failure: " + failure.getItem() + " - " + failure.error().getMessage());
                }
            };
            
            batcher.submitAsync("item-ok-2").thenAccept(asyncErrorHandler);
            batcher.submitAsync("item-error-2").thenAccept(asyncErrorHandler);
            
            Thread.sleep(300);
        }
    }
    
    /**
     * Example 3: Retry Configuration
     * Shows how to configure automatic retries for failed items.
     */
    private static void demonstrateRetryConfiguration() throws InterruptedException {
        System.out.println("\n=== Example 3: Retry Configuration ===\n");
        
        // Backend that fails on first attempt, succeeds on retry
        final int[] attemptCount = {0};
        Backend<String> backend = batch -> {
            attemptCount[0]++;
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (attemptCount[0] == 1) {
                    // Fail on first attempt
                    failures.add(new FailureEvent<>(item, new RuntimeException("Temporary failure - attempt " + attemptCount[0])));
                } else {
                    // Succeed on retry
                    successes.add(new SuccessEvent<>(item));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        // Configure retries
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(100))
            .maxRetries(2)  // Retry up to 2 times
            .retryDelay(Duration.ofMillis(50))  // Wait 50ms between retries
            .retryableErrorPredicate(error -> {
                // Only retry RuntimeException (not other exceptions)
                return error instanceof RuntimeException;
            })
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            System.out.println("Submitting items with retry configuration:");
            System.out.println("  - maxRetries: 2");
            System.out.println("  - retryDelay: 50ms");
            System.out.println("  - retryableErrorPredicate: RuntimeException only\n");
            
            ItemCallback<String> retryCallback = result -> {
                if (result instanceof ItemResult.Success<String> success) {
                    System.out.println("  ✓ Item eventually succeeded after retry: " + success.getItem());
                } else if (result instanceof ItemResult.Failure<String> failure) {
                    System.out.println("  ✗ Item failed after all retries: " + failure.getItem() + 
                        " - " + failure.error().getMessage());
                }
            };
            
            batcher.submit("item-retry", retryCallback);
            
            // Wait for retries to complete
            Thread.sleep(500);
        }
    }
}

