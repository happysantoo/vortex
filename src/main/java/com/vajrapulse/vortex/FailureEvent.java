package com.vajrapulse.vortex;

/**
 * Represents a failed request event.
 * 
 * @param <T> the type of request element
 */
public class FailureEvent<T> {
    private final T data;
    private final Throwable error;
    
    public FailureEvent(T data, Throwable error) {
        this.data = data;
        this.error = error;
    }
    
    public T getData() {
        return data;
    }
    
    public Throwable getError() {
        return error;
    }
}

