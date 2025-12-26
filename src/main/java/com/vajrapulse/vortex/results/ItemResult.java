package com.vajrapulse.vortex.results;

/**
 * A sealed interface representing the result of processing a single item in a batch.
 * This provides type-safe result handling with pattern matching support.
 * 
 * @param <T> the type of the item
 */
public sealed interface ItemResult<T> 
    permits ItemResult.Success, ItemResult.Failure {
    
    /**
     * Gets the item that was processed.
     * 
     * @return the item
     */
    T getItem();
    
    /**
     * Represents a successful item result.
     * 
     * @param <T> the type of the item
     * @param item the item that succeeded
     */
    record Success<T>(T item) implements ItemResult<T> {
        @Override
        public T getItem() {
            return item;
        }
    }
    
    /**
     * Represents a failed item result.
     * 
     * @param <T> the type of the item
     * @param item the item that failed
     * @param error the error that caused the failure
     */
    record Failure<T>(T item, Throwable error) implements ItemResult<T> {
        /**
         * Creates a new Failure result.
         * 
         * @param item the item that failed
         * @param error the error that caused the failure (must not be null)
         * @throws IllegalArgumentException if error is null
         */
        public Failure {
            if (error == null) {
                throw new IllegalArgumentException("Error must not be null");
            }
        }
        
        @Override
        public T getItem() {
            return item;
        }
    }
    
    /**
     * Creates a success result for the given item.
     * 
     * @param <T> the type of the item
     * @param item the item that succeeded
     * @return a Success result
     */
    static <T> ItemResult<T> success(T item) {
        return new Success<>(item);
    }
    
    /**
     * Creates a failure result for the given item and error.
     * 
     * @param <T> the type of the item
     * @param item the item that failed
     * @param error the error that caused the failure (must not be null)
     * @return a Failure result
     * @throws IllegalArgumentException if error is null
     */
    static <T> ItemResult<T> failure(T item, Throwable error) {
        return new Failure<>(item, error);
    }
    
    /**
     * Creates a success result from a SuccessEvent.
     * 
     * @param <T> the type of the item
     * @param event the success event
     * @return a Success result
     */
    static <T> ItemResult<T> success(SuccessEvent<T> event) {
        return new Success<>(event.data());
    }
    
    /**
     * Creates a failure result from a FailureEvent.
     * 
     * @param <T> the type of the item
     * @param event the failure event
     * @return a Failure result
     */
    static <T> ItemResult<T> failure(FailureEvent<T> event) {
        return new Failure<>(event.data(), event.error());
    }
}

