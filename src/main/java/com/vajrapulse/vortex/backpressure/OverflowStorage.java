package com.vajrapulse.vortex.backpressure;

/**
 * Interface for temporary storage of items when backpressure is detected.
 * 
 * <p>When backpressure is high, items can be stored in an overflow storage
 * instead of being rejected or dropped. Once backpressure is resolved, items
 * can be replayed from the overflow storage.
 * 
 * <p>Implementations should be thread-safe since they may be accessed from
 * multiple threads concurrently (submission threads and replay threads).
 * 
 * <p>Example implementations:
 * <ul>
 *   <li>{@link InMemoryOverflowStorage}: In-memory queue-based storage</li>
 *   <li>Disk-based storage: For very large overflow scenarios</li>
 *   <li>Distributed storage: For multi-instance scenarios</li>
 * </ul>
 * 
 * @param <T> the type of items being stored
 */
public interface OverflowStorage<T> {
    /**
     * Adds an item to the overflow storage.
     * 
     * <p>This method should be thread-safe and fast. It may be called from
     * multiple submission threads concurrently.
     * 
     * @param item the item to store
     * @throws IllegalStateException if storage is full and cannot accept more items
     */
    void add(T item);
    
    /**
     * Retrieves and removes an item from the overflow storage.
     * 
     * <p>This method should be thread-safe. It may be called from replay threads
     * while items are being added from submission threads.
     * 
     * @return the next item to replay, or null if storage is empty
     */
    T poll();
    
    /**
     * Checks if the overflow storage is empty.
     * 
     * @return true if storage is empty, false otherwise
     */
    boolean isEmpty();
    
    /**
     * Gets the number of items currently in overflow storage.
     * 
     * <p>This is a snapshot and may change immediately after returning.
     * 
     * @return the number of items in storage
     */
    int size();
    
    /**
     * Clears all items from the overflow storage.
     * 
     * <p>Useful for cleanup or reset scenarios.
     */
    void clear();
}

