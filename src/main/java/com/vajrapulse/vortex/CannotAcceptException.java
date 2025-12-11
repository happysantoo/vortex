package com.vajrapulse.vortex;

/**
 * Exception thrown when an item cannot be accepted due to capacity constraints.
 * 
 * <p>This exception is thrown in the following scenarios:
 * <ul>
 *   <li><strong>Queue Full</strong>: Internal queue has reached its capacity or rejection threshold</li>
 *   <li><strong>Concurrent Limit</strong>: Too many batches are being dispatched concurrently</li>
 * </ul>
 * 
 * <p>The exception includes metadata about:
 * <ul>
 *   <li>The current capacity level (e.g., current queue size vs max queue size)</li>
 *   <li>The source of the rejection (e.g., "Vortex Queue Depth", "Concurrent Batches")</li>
 * </ul>
 * 
 * <p><strong>Example Usage:</strong>
 * <pre>{@code
 * ItemResult<MyItem> result = batcher.submit(item, null);
 * if (result instanceof ItemResult.Failure<MyItem> failure) {
 *     if (failure.error() instanceof CannotAcceptException e) {
 *         logger.warn("Item rejected: {}", e.getMessage());
 *         // Handle rejection: store to overflow, retry, etc.
 *         storeToOverflow(item);
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Factory Methods:</strong>
 * <ul>
 *   <li>{@link #queueFull(int, int)} - For queue capacity rejections</li>
 *   <li>{@link #concurrentLimitReached(int, int)} - For concurrent batch limit rejections</li>
 * </ul>
 * 
 * @since 0.0.9
 */
public class CannotAcceptException extends RuntimeException {
    /** The current capacity level (e.g., current queue size). */
    private final int currentLevel;
    /** The maximum capacity level (e.g., max queue size). */
    private final int maxLevel;
    /** The name of the rejection source. */
    private final String sourceName;
    
    /**
     * Creates a new cannot accept exception.
     * 
     * @param message the error message
     * @param currentLevel the current capacity level
     * @param maxLevel the maximum capacity level
     * @param sourceName the name of the rejection source
     */
    public CannotAcceptException(String message, int currentLevel, int maxLevel, String sourceName) {
        super(message);
        this.currentLevel = currentLevel;
        this.maxLevel = maxLevel;
        this.sourceName = sourceName;
    }
    
    /**
     * Creates a new cannot accept exception with a cause.
     * 
     * @param message the error message
     * @param cause the cause of this exception
     * @param currentLevel the current capacity level
     * @param maxLevel the maximum capacity level
     * @param sourceName the name of the rejection source
     */
    public CannotAcceptException(String message, Throwable cause, int currentLevel, int maxLevel, String sourceName) {
        super(message, cause);
        this.currentLevel = currentLevel;
        this.maxLevel = maxLevel;
        this.sourceName = sourceName;
    }
    
    /**
     * Creates a cannot accept exception for queue full scenario.
     * 
     * <p>This is a convenience factory method for when the queue is at capacity or has reached
     * the rejection threshold.
     * 
     * @param currentSize the current queue size
     * @param maxSize the maximum queue size
     * @return a CannotAcceptException with source="Vortex Queue Depth"
     */
    public static CannotAcceptException queueFull(int currentSize, int maxSize) {
        return new CannotAcceptException(
            String.format("Queue full: %d/%d", currentSize, maxSize),
            currentSize,
            maxSize,
            "Vortex Queue Depth"
        );
    }
    
    /**
     * Creates a cannot accept exception for concurrent batch limit scenario.
     * 
     * <p>This is a convenience factory method for when too many batches are being dispatched.
     * 
     * @param activeBatches the current number of active batches
     * @param maxBatches the maximum allowed concurrent batches
     * @return a CannotAcceptException with source="Concurrent Batches"
     */
    public static CannotAcceptException concurrentLimitReached(int activeBatches, int maxBatches) {
        return new CannotAcceptException(
            String.format("Batch rejected: too many concurrent batches (active: %d, limit: %d)", 
                activeBatches, maxBatches),
            activeBatches,
            maxBatches,
            "Concurrent Batches"
        );
    }
    
    /**
     * Gets the current capacity level.
     * 
     * @return the current level (e.g., current queue size)
     */
    public int getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Gets the maximum capacity level.
     * 
     * @return the maximum level (e.g., max queue size)
     */
    public int getMaxLevel() {
        return maxLevel;
    }
    
    /**
     * Gets the name of the rejection source.
     * 
     * @return the source name
     */
    public String getSourceName() {
        return sourceName;
    }
}

