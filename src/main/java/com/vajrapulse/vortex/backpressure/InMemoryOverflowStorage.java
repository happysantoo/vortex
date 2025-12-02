package com.vajrapulse.vortex.backpressure;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

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
 * // Bounded storage with default capacity (1000 items)
 * OverflowStorage<String> overflow = new InMemoryOverflowStorage<>();
 * 
 * // Bounded storage with custom capacity
 * OverflowStorage<String> overflow = new InMemoryOverflowStorage<>(5000);
 * }</pre>
 * 
 * @param <T> the type of items being stored
 */
public class InMemoryOverflowStorage<T> implements OverflowStorage<T> {
    private static final int DEFAULT_CAPACITY = 1000; // Reasonable default for most use cases
    
    private final Queue<T> queue;
    private final int maxCapacity;
    
    /**
     * Creates a new in-memory overflow storage with the default capacity (1000 items).
     * 
     * <p>This constructor provides a bounded storage with a reasonable default capacity.
     * For custom capacity requirements, use {@link #InMemoryOverflowStorage(int)}.
     */
    public InMemoryOverflowStorage() {
        this(DEFAULT_CAPACITY);
    }
    
    /**
     * Creates a new in-memory overflow storage with the specified capacity.
     * 
     * @param maxCapacity the maximum number of items that can be stored (must be positive)
     * @throws IllegalArgumentException if maxCapacity is not positive
     */
    public InMemoryOverflowStorage(int maxCapacity) {
        if (maxCapacity <= 0) {
            throw new IllegalArgumentException(
                "Max capacity must be positive, got: " + maxCapacity
            );
        }
        this.maxCapacity = maxCapacity;
        this.queue = new ConcurrentLinkedQueue<>();
    }
    
    @Override
    public void add(T item) {
        if (item == null) {
            throw new IllegalArgumentException("Item cannot be null");
        }
        // Check capacity before adding (thread-safe check)
        if (queue.size() >= maxCapacity) {
            throw new IllegalStateException(
                "Overflow storage is full (capacity: " + maxCapacity + ", current size: " + queue.size() + ")"
            );
        }
        // Add item (may still exceed capacity slightly due to race condition, but that's acceptable)
        boolean added = queue.offer(item);
        if (!added) {
            // This should not happen with ConcurrentLinkedQueue (unbounded), but check defensively
            throw new IllegalStateException(
                "Failed to add item to overflow storage (capacity: " + maxCapacity + ", current size: " + queue.size() + ")"
            );
        }
        // Double-check after add to handle race conditions
        if (queue.size() > maxCapacity) {
            // Remove the item we just added if we exceeded capacity
            queue.remove(item);
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

