package com.vajrapulse.vortex.results;

/**
 * Represents a successful request event.
 * 
 * @param <T> the type of request element
 * @param data the data associated with the successful event
 */
public record SuccessEvent<T>(T data) {
}
