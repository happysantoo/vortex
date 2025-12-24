package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.SuccessEvent
import com.vajrapulse.vortex.results.FailureEvent
import java.util.concurrent.CountDownLatch

/**
 * Helper utilities for creating test backends.
 * 
 * <p>This class provides factory methods for common backend patterns used in tests,
 * reducing duplication and making tests more readable.
 */
class TestBackendHelpers {
    
    /**
     * Creates a backend that always succeeds (converts all items to SuccessEvent).
     * 
     * @param <T> the type of request elements
     * @return a Backend that always returns successful results
     */
    static <T> Backend<T> successBackend() {
        return { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
    }
    
    /**
     * Creates a backend that always fails (converts all items to FailureEvent).
     * 
     * @param <T> the type of request elements
     * @param error the error to use for failures (defaults to RuntimeException)
     * @return a Backend that always returns failure results
     */
    static <T> Backend<T> failingBackend(Throwable error = new RuntimeException("processing error")) {
        return { batch ->
            def failures = batch.collect { new FailureEvent<>(it, error) }
            new BatchResult<>(List.of(), failures)
        }
    }
    
    /**
     * Creates a backend that blocks until a latch is released.
     * Useful for testing queue rejection scenarios.
     * 
     * @param <T> the type of request elements
     * @param latch the CountDownLatch to wait on before processing
     * @return a Backend that blocks on the latch, then returns successful results
     */
    static <T> Backend<T> blockingBackend(CountDownLatch latch) {
        return { batch ->
            latch.await()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
    }
    
    /**
     * Creates a backend that records batches in a synchronized list.
     * Useful for verifying batch contents and timing.
     * 
     * @param <T> the type of request elements
     * @param recordedBatches a synchronized list to record batches in
     * @return a Backend that records batches and returns successful results
     */
    static <T> Backend<T> recordingBackend(List<BatchResult<T>> recordedBatches) {
        return { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            def result = new BatchResult<>(successes, List.of())
            recordedBatches.add(result)
            result
        }
    }
}

