package com.vajrapulse.vortex;

import java.util.ArrayList;
import java.util.List;

/**
 * A test backend that records all batches for testing purposes.
 * 
 * @param <T> the type of request elements
 */
public class TestBackend<T> implements Backend<T> {
    private final List<List<T>> recordedBatches = new ArrayList<>();
    private final java.util.function.Function<List<T>, BatchResult<T>> batchProcessor;
    
    /**
     * Creates a new TestBackend that always succeeds.
     */
    public TestBackend() {
        this.batchProcessor = batch -> {
            List<SuccessEvent<T>> successes = new ArrayList<>();
            for (T item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
    }
    
    /**
     * Creates a new TestBackend with a custom batch processor.
     * 
     * @param batchProcessor the function to process batches
     */
    public TestBackend(java.util.function.Function<List<T>, BatchResult<T>> batchProcessor) {
        this.batchProcessor = batchProcessor;
    }
    
    @Override
    public BatchResult<T> dispatch(List<T> batch) throws Exception {
        recordedBatches.add(new ArrayList<>(batch));
        return batchProcessor.apply(batch);
    }
    
    /**
     * Gets all recorded batches.
     * 
     * @return a list of all batches that were dispatched
     */
    public List<List<T>> getRecordedBatches() {
        return new ArrayList<>(recordedBatches);
    }
    
    /**
     * Gets the number of batches recorded.
     * 
     * @return the number of batches
     */
    public int getBatchCount() {
        return recordedBatches.size();
    }
    
    /**
     * Clears all recorded batches.
     */
    public void clear() {
        recordedBatches.clear();
    }
    
    /**
     * Gets the total number of items across all batches.
     * 
     * @return the total item count
     */
    public int getTotalItemCount() {
        return recordedBatches.stream()
            .mapToInt(List::size)
            .sum();
    }
}

