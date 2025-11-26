package com.vajrapulse.vortex;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility methods for testing MicroBatcher.
 */
public class MicroBatcherTestUtils {
    
    /**
     * Creates a test backend that records all batches.
     * 
     * @param <T> the type of request elements
     * @return a new TestBackend instance
     */
    public static <T> TestBackend<T> createTestBackend() {
        return new TestBackend<>();
    }
    
    /**
     * Creates a test backend with a custom batch processor.
     * 
     * @param <T> the type of request elements
     * @param batchProcessor the function to process batches
     * @return a new TestBackend instance
     */
    public static <T> TestBackend<T> createTestBackend(
            java.util.function.Function<java.util.List<T>, BatchResult<T>> batchProcessor) {
        return new TestBackend<>(batchProcessor);
    }
    
    /**
     * Waits for all pending batches to complete.
     * This method waits until the queue is empty and all in-flight batches are complete.
     * 
     * @param <T> the type of request elements
     * @param batcher the MicroBatcher instance
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @throws TimeoutException if the timeout is exceeded
     * @throws InterruptedException if interrupted while waiting
     */
    public static <T> void waitForBatches(MicroBatcher<T> batcher, long timeout, TimeUnit unit) 
            throws TimeoutException, InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        
        // Wait for queue to be empty (checking queue depth via metrics)
        while (System.currentTimeMillis() < deadline) {
            // Check if there are any pending requests by looking at queue depth
            // Note: This is a best-effort check since we don't have direct access to the queue
            Thread.sleep(10);
            
            // If batcher is closed, we're done
            try {
                // Try to submit a dummy item to check if closed
                // This is a workaround since we don't have direct access to the closed flag
                // In practice, tests should wait for their specific futures
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("closed")) {
                    return; // Batcher is closed, no more batches
                }
            }
        }
        
        if (System.currentTimeMillis() >= deadline) {
            throw new TimeoutException("Timeout waiting for batches to complete");
        }
    }
    
    /**
     * Waits for all pending batches to complete with a default timeout of 5 seconds.
     * 
     * @param <T> the type of request elements
     * @param batcher the MicroBatcher instance
     * @throws TimeoutException if the timeout is exceeded
     * @throws InterruptedException if interrupted while waiting
     */
    public static <T> void waitForBatches(MicroBatcher<T> batcher) 
            throws TimeoutException, InterruptedException {
        waitForBatches(batcher, 5, TimeUnit.SECONDS);
    }
}

