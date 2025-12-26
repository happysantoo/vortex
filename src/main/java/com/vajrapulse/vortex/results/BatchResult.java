package com.vajrapulse.vortex.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Represents the result of a batch dispatch operation.
 * 
 * @param <T> the type of request elements
 */
public class BatchResult<T> {
    private final List<SuccessEvent<T>> successes;
    private final List<FailureEvent<T>> failures;
    private final List<SuccessEvent<T>> unmodifiableSuccesses;
    private final List<FailureEvent<T>> unmodifiableFailures;
    
    /**
     * Creates a new BatchResult.
     * 
     * @param successes the list of successful events (may be null)
     * @param failures the list of failure events (may be null)
     */
    public BatchResult(List<SuccessEvent<T>> successes, List<FailureEvent<T>> failures) {
        // Use immutable empty list for null/empty inputs to avoid unnecessary allocations
        this.successes = (successes == null || successes.isEmpty()) 
            ? List.of() 
            : new ArrayList<>(successes);
        this.failures = (failures == null || failures.isEmpty())
            ? List.of()
            : new ArrayList<>(failures);
        
        // Cache unmodifiable views at construction to avoid allocations on repeated getter calls
        this.unmodifiableSuccesses = Collections.unmodifiableList(this.successes);
        this.unmodifiableFailures = Collections.unmodifiableList(this.failures);
    }
    
    /**
     * Gets the list of successful events.
     * 
     * @return an unmodifiable list of successful events
     */
    public List<SuccessEvent<T>> getSuccesses() {
        return unmodifiableSuccesses;
    }
    
    /**
     * Gets the list of failure events.
     * 
     * @return an unmodifiable list of failure events
     */
    public List<FailureEvent<T>> getFailures() {
        return unmodifiableFailures;
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
     * Checks if all requests in the batch succeeded.
     * This is an alias for {@link #isAllSuccess()} for consistency with {@link #isCompleteFailure()}.
     * 
     * @return true if all requests succeeded, false otherwise
     */
    public boolean isCompleteSuccess() {
        return isAllSuccess();
    }
    
    /**
     * Checks if all requests in the batch failed.
     * 
     * @return true if all requests failed, false otherwise
     */
    public boolean isCompleteFailure() {
        return successes.isEmpty();
    }
    
    /**
     * Gets the failure rate as a value between 0.0 and 1.0.
     * 
     * @return the failure rate (0.0 = no failures, 1.0 = all failures)
     */
    public double getFailureRate() {
        int total = successes.size() + failures.size();
        return total == 0 ? 0.0 : (double) failures.size() / total;
    }
    
    /**
     * Gets a summary of failures grouped by error type.
     * 
     * @return a map from error class to list of failure events with that error type
     */
    public Map<Class<? extends Throwable>, List<FailureEvent<T>>> getFailuresByType() {
        return failures.stream()
            .collect(Collectors.groupingBy(
                f -> f.error().getClass().asSubclass(Throwable.class)));
    }
    
    /**
     * Gets the total count of requests in the batch.
     * 
     * @return the total count of requests
     */
    public int getTotalCount() {
        return successes.size() + failures.size();
    }
    
    /**
     * Finds the result for a specific item using the default equality comparator (Objects::equals).
     * 
     * @param item the item to find
     * @return Optional containing ItemResult for the item, or empty if not found
     */
    public Optional<ItemResult<T>> findItemResult(T item) {
        return findItemResult(item, Objects::equals);
    }
    
    /**
     * Finds the result for a specific item using a custom equality comparator.
     * 
     * @param item the item to find
     * @param equalityComparator the comparator to use for item equality
     * @return Optional containing ItemResult for the item, or empty if not found
     */
    public Optional<ItemResult<T>> findItemResult(T item, BiPredicate<T, T> equalityComparator) {
        // Check successes first
        for (SuccessEvent<T> success : successes) {
            T successData = success.data();
            if (successData != null && equalityComparator.test(successData, item)) {
                return Optional.of(ItemResult.success(success));
            } else if (successData == null && item == null) {
                return Optional.of(ItemResult.success(success));
            }
        }
        
        // Check failures
        for (FailureEvent<T> failure : failures) {
            T failureData = failure.data();
            if (failureData != null && equalityComparator.test(failureData, item)) {
                return Optional.of(ItemResult.failure(failure));
            } else if (failureData == null && item == null) {
                return Optional.of(ItemResult.failure(failure));
            }
        }
        
        return Optional.empty();
    }
}

