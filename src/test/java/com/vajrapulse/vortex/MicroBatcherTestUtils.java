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
     * <p>Note: This is a best-effort utility. In practice, tests should wait for their
     * specific CompletableFuture instances rather than using this method.
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
            // Check if batcher is closed
            if (batcher.isClosed()) {
                // If closed, wait a bit more for any in-flight batches, then return
                Thread.sleep(50);
                return;
            }
            
            // Check queue depth via metrics
            double queueDepth = batcher.getMeterRegistry()
                .gauge("vortex.queue.depth", 0.0);
            
            // If queue is empty and batcher is not closed, we might be done
            // But we need to account for in-flight batches, so wait a bit more
            if (queueDepth == 0.0) {
                Thread.sleep(50); // Give time for in-flight batches to complete
                // Check again to make sure queue is still empty
                double currentDepth = batcher.getMeterRegistry()
                    .gauge("vortex.queue.depth", 0.0);
                if (currentDepth == 0.0) {
                    return; // Queue is empty and stayed empty
                }
            }
            
            Thread.sleep(10);
        }
        
        // Timeout reached
        throw new TimeoutException("Timeout waiting for batches to complete");
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

