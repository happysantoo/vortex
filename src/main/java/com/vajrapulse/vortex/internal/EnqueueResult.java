package com.vajrapulse.vortex.internal;

/**
 * Result of attempting to enqueue a pending request into the internal queue.
 * 
 * <p>For rejection results, this includes the queue size at the time of rejection
 * to provide accurate diagnostics and avoid stale data issues.
 */
public class EnqueueResult {
    /** The request was accepted and enqueued. */
    public static final EnqueueResult ACCEPTED = new EnqueueResult(Type.ACCEPTED, 0, 0);
    
    private final Type type;
    private final int queueSizeAtRejection;
    private final int maxQueueSize;
    
    private EnqueueResult(Type type, int queueSizeAtRejection, int maxQueueSize) {
        this.type = type;
        this.queueSizeAtRejection = queueSizeAtRejection;
        this.maxQueueSize = maxQueueSize;
    }
    
    /**
     * Creates a rejection result with queue size information.
     * 
     * @param type the rejection type (REJECTED_THRESHOLD or REJECTED_FULL)
     * @param queueSizeAtRejection the queue size at the time of rejection
     * @param maxQueueSize the maximum queue size
     * @return an EnqueueResult with rejection information
     */
    public static EnqueueResult rejected(Type type, int queueSizeAtRejection, int maxQueueSize) {
        if (type != Type.REJECTED_THRESHOLD && 
            type != Type.REJECTED_FULL && 
            type != Type.REJECTED_CONCURRENT_BATCHES) {
            throw new IllegalArgumentException(
                "Type must be REJECTED_THRESHOLD, REJECTED_FULL, or REJECTED_CONCURRENT_BATCHES");
        }
        return new EnqueueResult(type, queueSizeAtRejection, maxQueueSize);
    }
    
    /**
     * Creates an interrupted result.
     * 
     * @return an EnqueueResult indicating interruption
     */
    public static EnqueueResult interrupted() {
        return new EnqueueResult(Type.INTERRUPTED, 0, 0);
    }
    
    /**
     * Gets the result type.
     * 
     * @return the result type
     */
    public Type getType() {
        return type;
    }
    
    /**
     * Gets the queue size at the time of rejection (only valid for rejection results).
     * 
     * @return the queue size at rejection time, or 0 if not a rejection
     */
    public int getQueueSizeAtRejection() {
        return queueSizeAtRejection;
    }
    
    /**
     * Gets the maximum queue size (only valid for rejection results).
     * 
     * @return the maximum queue size, or 0 if not a rejection
     */
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    /**
     * Checks if the result indicates acceptance.
     * 
     * @return true if accepted, false otherwise
     */
    public boolean isAccepted() {
        return type == Type.ACCEPTED;
    }
    
    /**
     * Checks if the result indicates rejection.
     * 
     * @return true if rejected, false otherwise
     */
    public boolean isRejected() {
        return type == Type.REJECTED_THRESHOLD || 
               type == Type.REJECTED_FULL || 
               type == Type.REJECTED_CONCURRENT_BATCHES;
    }
    
    /**
     * Checks if the result indicates interruption.
     * 
     * @return true if interrupted, false otherwise
     */
    public boolean isInterrupted() {
        return type == Type.INTERRUPTED;
    }
    
    /**
     * Result type enumeration.
     */
    public enum Type {
        /** The request was accepted and enqueued. */
        ACCEPTED,
        /** The request was rejected because the queue reached the rejection threshold. */
        REJECTED_THRESHOLD,
        /** The request was rejected because the queue is full. */
        REJECTED_FULL,
        /** The request was rejected because concurrent batch limit would prevent dispatch. */
        REJECTED_CONCURRENT_BATCHES,
        /** The request was interrupted while attempting to enqueue. */
        INTERRUPTED
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EnqueueResult that = (EnqueueResult) o;
        return type == that.type && 
               queueSizeAtRejection == that.queueSizeAtRejection && 
               maxQueueSize == that.maxQueueSize;
    }
    
    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + queueSizeAtRejection;
        result = 31 * result + maxQueueSize;
        return result;
    }
    
    @Override
    public String toString() {
        if (type == Type.ACCEPTED) {
            return "EnqueueResult{ACCEPTED}";
        } else if (type == Type.INTERRUPTED) {
            return "EnqueueResult{INTERRUPTED}";
        } else {
            return String.format("EnqueueResult{%s, queueSize=%d/%d}", 
                type, queueSizeAtRejection, maxQueueSize);
        }
    }
}

