package com.vajrapulse.vortex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the result of a batch dispatch operation.
 * 
 * @param <T> the type of request elements
 */
public class BatchResult<T> {
    private final List<SuccessEvent<T>> successes;
    private final List<FailureEvent<T>> failures;
    
    /**
     * Creates a new BatchResult.
     * 
     * @param successes the list of successful events (may be null)
     * @param failures the list of failure events (may be null)
     */
    public BatchResult(List<SuccessEvent<T>> successes, List<FailureEvent<T>> failures) {
        this.successes = successes != null ? new ArrayList<>(successes) : new ArrayList<>();
        this.failures = failures != null ? new ArrayList<>(failures) : new ArrayList<>();
    }
    
    /**
     * Gets the list of successful events.
     * 
     * @return an unmodifiable list of successful events
     */
    public List<SuccessEvent<T>> getSuccesses() {
        return Collections.unmodifiableList(successes);
    }
    
    /**
     * Gets the list of failure events.
     * 
     * @return an unmodifiable list of failure events
     */
    public List<FailureEvent<T>> getFailures() {
        return Collections.unmodifiableList(failures);
    }
    
    /**
     * Checks if all requests in the batch succeeded.
     * 
     * @return true if all requests succeeded, false otherwise
     */
    public boolean isAllSuccess() {
        return failures.isEmpty();
    }
    
    /**
     * Gets the total count of requests in the batch.
     * 
     * @return the total count of requests
     */
    public int getTotalCount() {
        return successes.size() + failures.size();
    }
}

