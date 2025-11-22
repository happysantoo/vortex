package com.vajrapulse.vortex;

/**
 * Represents a failed request event.
 * 
 * @param <T> the type of request element
 */
public class FailureEvent<T> {
    private final T data;
    private final Throwable error;
    
    /**
     * Creates a new FailureEvent.
     * 
     * @param data the data associated with the failed event
     * @param error the error that caused the failure
     */
    public FailureEvent(T data, Throwable error) {
        this.data = data;
        this.error = error;
    }
    
    /**
     * Gets the data associated with this failed event.
     * 
     * @return the data
     */
    public T getData() {
        return data;
    }
    
    /**
     * Gets the error that caused the failure.
     * 
     * @return the error
     */
    public Throwable getError() {
        return error;
    }
}

