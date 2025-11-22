package com.vajrapulse.vortex;

import java.util.List;

/**
 * Interface for dispatching batches to a backend.
 * The dispatch method can be blocking - it will be executed on a virtual thread.
 * 
 * @param <T> the type of request elements
 */
public interface Backend<T> {
    
    /**
     * Dispatches a batch of requests to the backend.
     * This method can be blocking (e.g., making HTTP calls, database queries).
     * It will be executed on a virtual thread for efficient I/O operations.
     * 
     * @param batch the batch of requests to dispatch
     * @return the batch result containing successes and failures
     * @throws Exception if the dispatch fails
     */
    BatchResult<T> dispatch(List<T> batch) throws Exception;
    
    /**
     * Determines whether successful items should be replayed when a batch contains both successes and failures.
     * 
     * This allows backends to decide replay behavior based on their nature:
     * - Atomic backends (e.g., DB inserts with unique constraints) may need replay for rejected items
     * - Backends that handle success/failures internally may not need replay
     * 
     * @param result the batch result containing successes and failures
     * @return true if successful items should be replayed, false otherwise
     */
    default boolean shouldReplaySuccesses(BatchResult<T> result) {
        // Default implementation: don't replay
        // Backends can override this to customize replay behavior
        return false;
    }
}

