package com.vajrapulse.vortex.backpressure;

/**
 * Exception thrown when an item is rejected due to backpressure.
 * 
 * <p>This exception is used by strategies (e.g., {@link RejectStrategy}) to indicate
 * that an item was rejected because the system is under backpressure.
 * 
 * <p>The exception includes details about:
 * <ul>
 *   <li>The backpressure level that triggered the rejection</li>
 *   <li>The threshold that was exceeded</li>
 *   <li>The source of the backpressure</li>
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
     * @param backpressureLevel the backpressure level that triggered the rejection
     * @param threshold the threshold that was exceeded
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
     * @param backpressureLevel the backpressure level that triggered the rejection
     * @param threshold the threshold that was exceeded
     * @param sourceName the name of the backpressure source
     */
    public BackpressureException(String message, Throwable cause, double backpressureLevel, double threshold, String sourceName) {
        super(message, cause);
        this.backpressureLevel = backpressureLevel;
        this.threshold = threshold;
        this.sourceName = sourceName;
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

