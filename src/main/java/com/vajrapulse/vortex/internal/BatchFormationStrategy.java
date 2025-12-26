package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Handles batch formation logic, collecting items from the queue up to the configured
 * batch size or until the linger time expires.
 *
 * @param <T> the type of item being batched
 */
public class BatchFormationStrategy<T> {
    private static final Logger logger = LoggerFactory.getLogger(BatchFormationStrategy.class);
    
    private final BatcherConfig config;
    private final BlockingQueue<PendingRequest<T>> queue;
    private final boolean debugMode;
    
    /**
     * Creates a new BatchFormationStrategy.
     *
     * @param config the batcher configuration
     * @param queue the blocking queue to poll items from
     * @param debugMode whether debug mode is enabled
     */
    public BatchFormationStrategy(BatcherConfig config, BlockingQueue<PendingRequest<T>> queue, boolean debugMode) {
        this.config = config;
        this.queue = queue;
        this.debugMode = debugMode;
    }
    
    /**
     * Forms a batch by collecting items from the queue.
     * 
     * <p>This method:
     * <ul>
     *   <li>Waits for the first item with a timeout based on linger time</li>
     *   <li>Collects up to batchSize items, respecting the linger time deadline</li>
     *   <li>Returns an empty list if no items are available</li>
     * </ul>
     * 
     * @return a list of pending requests forming a batch, or an empty list if no items available
     * @throws InterruptedException if interrupted while waiting for items
     */
    public List<PendingRequest<T>> formBatch() throws InterruptedException {
        // Pre-size batch list to avoid resizing
        int batchSize = config.getBatchSize();
        List<PendingRequest<T>> batch = new ArrayList<>(batchSize);
        
        Duration lingerTime = config.getLingerTime();
        long lingerTimeMillis = lingerTime.toMillis();
        
        // Wait for first item with timeout based on linger time
        PendingRequest<T> first = queue.poll(lingerTimeMillis, TimeUnit.MILLISECONDS);
        if (first == null) {
            return batch; // Empty batch
        }
        
        batch.add(first);
        
        logger.debug("Starting batch formation, first item: {}", first.data());
        
        // Collect up to batchSize items, respecting linger time
        long deadline = System.currentTimeMillis() + lingerTimeMillis;
        while (batch.size() < batchSize) {
            long remainingMillis = Math.max(1, deadline - System.currentTimeMillis());
            if (remainingMillis <= 0) {
                logger.debug("Linger time elapsed, batch size: {}", batch.size());
                break;
            }
            
            PendingRequest<T> next = queue.poll(remainingMillis, TimeUnit.MILLISECONDS);
            if (next == null) {
                logger.debug("Timeout waiting for next item, batch size: {}", batch.size());
                break;
            }
            batch.add(next);
            logger.debug("Added item to batch, current size: {}, queue depth: {}", 
                batch.size(), queue.size());
        }
        
        if (!batch.isEmpty()) {
            logger.debug("Formed batch of size: {}", batch.size());
        }
        
        return batch;
    }
}

