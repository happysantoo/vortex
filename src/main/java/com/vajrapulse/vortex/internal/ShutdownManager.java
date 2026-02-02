package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.Backend;
import com.vajrapulse.vortex.results.BatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles shutdown orchestration for the MicroBatcher, including queue draining,
 * executor shutdown, and processing remaining items.
 *
 * @param <T> the type of item being processed
 */
public class ShutdownManager<T> {
    private static final int CLOSE_POLL_INTERVAL_MS = 10;
    
    private final BlockingQueue<PendingRequest<T>> queue;
    private final ExecutorService dispatchExecutor;
    private final ExecutorService retryExecutor;
    private final AtomicInteger activeBatchCount;
    private final Backend<T> backend;
    private final ResultProcessor<T> resultProcessor;
    private final RetryManager<T> retryManager;
    private final long queueDrainTimeoutMillis;
    private final long executorShutdownTimeoutSeconds;
    
    /**
     * Creates a new ShutdownManager.
     *
     * @param queue the blocking queue of pending requests
     * @param dispatchExecutor the executor service for dispatch operations
     * @param retryExecutor the executor service for retry operations
     * @param activeBatchCount the atomic integer tracking active batches (may be null)
     * @param backend the backend for processing remaining items
     * @param resultProcessor the result processor for processing batch results
     * @param retryManager the retry manager for clearing retry state
     * @param queueDrainTimeoutMillis the timeout for waiting for queue to drain (in milliseconds)
     * @param executorShutdownTimeoutSeconds the timeout for executor shutdown (in seconds)
     */
    public ShutdownManager(
            BlockingQueue<PendingRequest<T>> queue,
            ExecutorService dispatchExecutor,
            ExecutorService retryExecutor,
            AtomicInteger activeBatchCount,
            Backend<T> backend,
            ResultProcessor<T> resultProcessor,
            RetryManager<T> retryManager,
            long queueDrainTimeoutMillis,
            long executorShutdownTimeoutSeconds) {
        this.queue = queue;
        this.dispatchExecutor = dispatchExecutor;
        this.retryExecutor = retryExecutor;
        this.activeBatchCount = activeBatchCount;
        this.backend = backend;
        this.resultProcessor = resultProcessor;
        this.retryManager = retryManager;
        this.queueDrainTimeoutMillis = queueDrainTimeoutMillis;
        this.executorShutdownTimeoutSeconds = executorShutdownTimeoutSeconds;
    }
    
    /**
     * Performs graceful shutdown of the batcher.
     * 
     * <p>This method:
     * <ul>
     *   <li>Clears retry manager state</li>
     *   <li>Waits for the batch processor to finish processing items already in the queue (up to 2 seconds)</li>
     *   <li>Shuts down the executor gracefully, allowing in-flight batches to complete (up to 5 seconds)</li>
     *   <li>Processes any remaining items synchronously after executor shutdown</li>
     * </ul>
     */
    public void shutdown() {
        retryManager.clearAll();

        // Best-effort wait for the batch processor to drain the queue
        waitForQueueToDrain(queueDrainTimeoutMillis, TimeUnit.MILLISECONDS);
        
        // Shutdown both executors gracefully to allow in-flight operations to complete
        dispatchExecutor.shutdown();
        retryExecutor.shutdown();
        
        try {
            // Wait for dispatch executor
            if (!dispatchExecutor.awaitTermination(executorShutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                dispatchExecutor.shutdownNow();
            }
            // Wait for retry executor
            if (!retryExecutor.awaitTermination(executorShutdownTimeoutSeconds, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dispatchExecutor.shutdownNow();
            retryExecutor.shutdownNow();
        }
        
        // Wait for all in-flight batches to complete (if concurrent limiting is enabled)
        try {
            awaitInFlightBatches(executorShutdownTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Process any remaining items synchronously after executors are done
        List<PendingRequest<T>> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            // Create data list with pre-sized ArrayList (optimization: avoid stream overhead)
            List<T> dataList = new ArrayList<>(remaining.size());
            for (PendingRequest<T> req : remaining) {
                dataList.add(req.data());
            }
            
            try {
                BatchResult<T> result = backend.dispatch(dataList);
                resultProcessor.processResults(remaining, result);
            } catch (Exception e) {
                resultProcessor.processFailure(remaining, e);
            }
        }
    }
    
    /**
     * Waits for all queued items and in-flight batches to complete.
     * 
     * <p>This method provides a way to gracefully wait for all processing to complete
     * before closing the batcher. It waits for:
     * <ul>
     *   <li>All items in the queue to be processed</li>
     *   <li>All in-flight batches to complete</li>
     * </ul>
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @param isClosed whether the batcher is already closed
     * @return true if all items completed within the timeout, false if timeout was reached
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public boolean awaitCompletion(long timeout, TimeUnit unit, boolean isClosed) throws InterruptedException {
        if (isClosed) {
            // If already closed, just wait for in-flight batches
            return awaitInFlightBatches(timeout, unit);
        }

        long timeoutMillis = unit.toMillis(timeout);

        // Wait for queue to drain
        long remainingAfterQueue = waitForQueueToDrain(timeoutMillis, TimeUnit.MILLISECONDS);
        if (remainingAfterQueue <= 0) {
            return queue.isEmpty() && (activeBatchCount == null || activeBatchCount.get() == 0);
        }

        // Wait for in-flight batches with remaining time
        return awaitInFlightBatches(remainingAfterQueue, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Waits for all in-flight batches to complete.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return true if all batches completed within the timeout, false otherwise
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    private boolean awaitInFlightBatches(long timeout, TimeUnit unit) throws InterruptedException {
        if (activeBatchCount == null) {
            // No concurrent limiting - just wait for dispatch executor to finish
            if (dispatchExecutor.isShutdown()) {
                return dispatchExecutor.awaitTermination(timeout, unit);
            }
            // Executor not shut down yet - can't reliably wait
            return true;
        }
        
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (activeBatchCount.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Interrupted while waiting for in-flight batches");
            }
        }
        
        return activeBatchCount.get() == 0;
    }

    /**
     * Waits for the internal queue to drain, up to the given timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout
     * @return remaining time budget in milliseconds (may be zero or negative if timed out)
     */
    private long waitForQueueToDrain(long timeout, TimeUnit unit) {
        long timeoutMillis = unit.toMillis(timeout);
        long deadline = System.currentTimeMillis() + timeoutMillis;

        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(CLOSE_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return deadline - System.currentTimeMillis();
    }
}

