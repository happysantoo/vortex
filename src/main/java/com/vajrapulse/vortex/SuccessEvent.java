package com.vajrapulse.vortex;

/**
 * Represents a successful request event.
 * 
 * @param <T> the type of request element
 */
public class SuccessEvent<T> {
    private final T data;
    
    /**
     * Creates a new SuccessEvent.
     * 
     * @param data the data associated with the successful event
     */
    public SuccessEvent(T data) {
        this.data = data;
    }
    
    /**
     * Gets the data associated with this successful event.
     * 
     * @return the data
     */
    public T getData() {
        return data;
    }
}

