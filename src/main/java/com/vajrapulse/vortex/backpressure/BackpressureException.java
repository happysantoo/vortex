package com.vajrapulse.vortex.backpressure;

/**
 * Exception thrown when an item is rejected due to capacity constraints.
 * 
 * <p>This is the <strong>unified exception</strong> for all rejection scenarios:
 * <ul>
 *   <li><strong>Backpressure</strong>: System is under pressure (e.g., queue depth > threshold)</li>
 *   <li><strong>Queue Full</strong>: Internal queue is at capacity</li>
 *   <li><strong>Concurrent Limit</strong>: Too many batches being dispatched concurrently</li>
 * </ul>
 * 
 * <p>The exception includes rich metadata about:
 * <ul>
 *   <li>The backpressure level that triggered the rejection (0.0 to 1.0)</li>
 *   <li>The threshold that was exceeded (0.0 to 1.0)</li>
 *   <li>The source of the backpressure (e.g., "Vortex Queue Depth", "Concurrent Batches")</li>
 * </ul>
 * 
 * <p><strong>Unified Exception Handling:</strong>
 * <p>From the application's perspective, all rejections mean "can't accept item right now"
 * and should be handled the same way. Applications can catch this single exception type:
 * <pre>{@code
 * try {
 *     batcher.submit(item);
 * } catch (BackpressureException e) {
 *     // Handles all rejection cases: backpressure, queue full, concurrent limit
 *     logger.warn("Rejected: level={:.2f}, threshold={:.2f}, source={}", 
 *         e.getBackpressureLevel(), e.getThreshold(), e.getSourceName());
 *     // Unified handling: store to overflow, retry, etc.
 *     storeToOverflow(item);
 * }
 * }</pre>
 * 
 * <p><strong>Factory Methods:</strong>
 * <ul>
 *   <li>{@link #queueFull(int, int)} - For queue capacity rejections</li>
 *   <li>{@link #concurrentLimitReached(int, int)} - For concurrent batch limit rejections</li>
 * </ul>
 */
public class BackpressureException extends RuntimeException {
    /** The backpressure level that triggered the rejection (0.0 to 1.0). */
    private final double backpressureLevel;
    /** The threshold that was exceeded (0.0 to 1.0). */
    private final double threshold;
    /** The name of the backpressure source. */
    private final String sourceName;
    
    /**
     * Creates a new backpressure exception.
     * 
     * @param message the error message
     * @param backpressureLevel the backpressure level that triggered the rejection (0.0 to 1.0)
     * @param threshold the threshold that was exceeded (0.0 to 1.0)
     * @param sourceName the name of the backpressure source
     */
    public BackpressureException(String message, double backpressureLevel, double threshold, String sourceName) {
        super(message);
        this.backpressureLevel = backpressureLevel;
        this.threshold = threshold;
        this.sourceName = sourceName;
    }
    
    /**
     * Creates a new backpressure exception with a cause.
     * 
     * @param message the error message
     * @param cause the cause of this exception
     * @param backpressureLevel the backpressure level that triggered the rejection (0.0 to 1.0)
     * @param threshold the threshold that was exceeded (0.0 to 1.0)
     * @param sourceName the name of the backpressure source
     */
    public BackpressureException(String message, Throwable cause, double backpressureLevel, double threshold, String sourceName) {
        super(message, cause);
        this.backpressureLevel = backpressureLevel;
        this.threshold = threshold;
        this.sourceName = sourceName;
    }
    
    /**
     * Creates a backpressure exception for queue full scenario.
     * 
     * <p>This is a convenience factory method for when the queue is at capacity.
     * 
     * @param currentSize the current queue size
     * @param maxSize the maximum queue size
     * @return a BackpressureException with level=1.0, threshold=1.0, source="Vortex Queue Depth"
     */
    public static BackpressureException queueFull(int currentSize, int maxSize) {
        return new BackpressureException(
            String.format("Queue full: %d/%d", currentSize, maxSize),
            1.0,  // 100% capacity
            1.0,  // 100% threshold
            "Vortex Queue Depth"
        );
    }
    
    /**
     * Creates a backpressure exception for concurrent batch limit scenario.
     * 
     * <p>This is a convenience factory method for when too many batches are being dispatched.
     * 
     * @param activeBatches the current number of active batches
     * @param maxBatches the maximum allowed concurrent batches
     * @return a BackpressureException with appropriate level and source
     */
    public static BackpressureException concurrentLimitReached(int activeBatches, int maxBatches) {
        double level = maxBatches > 0 ? (double) activeBatches / maxBatches : 1.0;
        return new BackpressureException(
            String.format("Batch rejected: too many concurrent batches (active: %d, limit: %d)", 
                activeBatches, maxBatches),
            level,
            1.0,  // 100% threshold
            "Concurrent Batches"
        );
    }
    
    /**
     * Gets the backpressure level that triggered the rejection.
     * 
     * @return the backpressure level (0.0 to 1.0)
     */
    public double getBackpressureLevel() {
        return backpressureLevel;
    }
    
    /**
     * Gets the threshold that was exceeded.
     * 
     * @return the threshold (0.0 to 1.0)
     */
    public double getThreshold() {
        return threshold;
    }
    
    /**
     * Gets the name of the backpressure source.
     * 
     * @return the source name
     */
    public String getSourceName() {
        return sourceName;
    }
}

