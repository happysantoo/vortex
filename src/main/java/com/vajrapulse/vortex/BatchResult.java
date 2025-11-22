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
    
    public BatchResult(List<SuccessEvent<T>> successes, List<FailureEvent<T>> failures) {
        this.successes = successes != null ? new ArrayList<>(successes) : new ArrayList<>();
        this.failures = failures != null ? new ArrayList<>(failures) : new ArrayList<>();
    }
    
    public List<SuccessEvent<T>> getSuccesses() {
        return Collections.unmodifiableList(successes);
    }
    
    public List<FailureEvent<T>> getFailures() {
        return Collections.unmodifiableList(failures);
    }
    
    public boolean isAllSuccess() {
        return failures.isEmpty();
    }
    
    public int getTotalCount() {
        return successes.size() + failures.size();
    }
}

