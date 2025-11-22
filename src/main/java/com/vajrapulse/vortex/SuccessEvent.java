package com.vajrapulse.vortex;

/**
 * Represents a successful request event.
 * 
 * @param <T> the type of request element
 */
public class SuccessEvent<T> {
    private final T data;
    
    public SuccessEvent(T data) {
        this.data = data;
    }
    
    public T getData() {
        return data;
    }
}

