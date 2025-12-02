package com.vajrapulse.vortex.backpressure;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory overflow storage implementation using a blocking queue.
 * 
 * <p>This is a simple, thread-safe implementation suitable for most use cases.
 * Items are stored in a bounded queue. When the queue is full, adding new items
 * will throw an {@link IllegalStateException}.
 * 
 * <p>This implementation is suitable for:
 * <ul>
 *   <li>Moderate overflow scenarios (hundreds to thousands of items)</li>
 *   <li>Short-term backpressure (minutes, not hours)</li>
 *   <li>Single-instance deployments</li>
 * </ul>
 * 
 * <p>For very large overflow scenarios or distributed deployments, consider
 * implementing a custom {@link OverflowStorage} using disk or distributed storage.
 * 
 * <p>Example usage:
 * <pre>{@code
 * OverflowStorage<String> overflow = new InMemoryOverflowStorage<>(1000);
 * }</pre>
 * 
 * @param <T> the type of items being stored
 */
public class InMemoryOverflowStorage<T> implements OverflowStorage<T> {
    private final BlockingQueue<T> queue;
    private final int maxCapacity;
    
    /**
     * Creates a new in-memory overflow storage with the specified capacity.
     * 
     * @param maxCapacity the maximum number of items that can be stored
     * @throws IllegalArgumentException if maxCapacity is not positive
     */
    public InMemoryOverflowStorage(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException(
                "Max capacity must be positive, got: " + maxCapacity
            );
        }
        this.maxCapacity = maxCapacity;
        this.queue = new LinkedBlockingQueue<>(maxCapacity);
    }
    
    @Override
    public void add(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }
        if (!queue.offer(item)) {
            throw new IllegalStateException(
                "Overflow storage is full (capacity: " + maxCapacity + ")"
            );
        }
    }
    
    @Override
    public T poll() {
        return queue.poll();
    }
    
    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }
    
    @Override
    public int size() {
        return queue.size();
    }
    
    @Override
    public void clear() {
        queue.clear();
    }
    
    /**
     * Gets the maximum capacity of this storage.
     * 
     * @return the maximum capacity
     */
    public int getMaxCapacity() {
        return maxCapacity;
    }
}

