package com.vajrapulse.vortex.results;

/**
 * Represents a failed request event.
 * 
 * @param <T> the type of request element
 * @param data the data associated with the failed event
 * @param error the error that caused the failure
 */
public record FailureEvent<T>(T data, Throwable error) {
}
