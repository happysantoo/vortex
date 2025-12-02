package com.vajrapulse.vortex.backpressure;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Strategy that overflows items to temporary storage when backpressure is high.
 * 
 * <p>Lifecycle:
 * <ol>
 *   <li>When backpressure enters: Execute onPause callback (if provided), start storing items to overflow</li>
 *   <li>While backpressure active: Continue storing items, monitor for resolution</li>
 *   <li>When backpressure resolves: Replay items from overflow, execute onResume callback (if provided)</li>
 * </ol>
 * 
 * <p>Useful for scenarios where:
 * <ul>
 *   <li>Items must be processed (cannot drop or reject)</li>
 *   <li>External consumers (e.g., Kafka) should be paused during backpressure</li>
 *   <li>Items should be replayed once backpressure is resolved</li>
 * </ul>
 * 
 * <p>Example usage with Kafka:
 * <pre>{@code
 * OverflowStorage<String> overflow = new InMemoryOverflowStorage<>(1000);
 * OverflowStrategy<String> strategy = new OverflowStrategy<>(
 *     0.7,
 *     overflow,
 *     queueProvider,
 *     batcher::submit,
 *     () -> kafkaConsumer.pause(consumer.assignment()),  // onPause
 *     () -> kafkaConsumer.resume(consumer.assignment())   // onResume
 * );
 * }</pre>
 * 
 * @param <T> the type of items being handled
 */
public class OverflowStrategy<T> implements LifecycleAwareStrategy<T> {
    private final double threshold;
    private final OverflowStorage<T> overflowStorage;
    private final Runnable onPause;  // Optional callback for pausing consumer
    private final Runnable onResume; // Optional callback for resuming consumer
    private final BackpressureProvider backpressureProvider;
    private final Function<T, CompletableFuture<com.vajrapulse.vortex.BatchResult<T>>> submitFunction;
    
    /**
     * Creates a new overflow strategy without pause/resume callbacks.
     * 
     * <p>Use this constructor when you don't need to pause/resume external systems.
     * Items will still be stored in overflow and replayed when backpressure resolves.
     * 
     * @param threshold the backpressure threshold (0.0 to 1.0)
     * @param overflowStorage the storage for overflow items
     * @param backpressureProvider the provider to check for backpressure resolution
     * @param submitFunction function to submit items for replay
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public OverflowStrategy(
            double threshold,
            OverflowStorage<T> overflowStorage,
            BackpressureProvider backpressureProvider,
            Function<T, CompletableFuture<com.vajrapulse.vortex.BatchResult<T>>> submitFunction) {
        this(threshold, overflowStorage, backpressureProvider, submitFunction, null, null);
    }
    
    /**
     * Creates a new overflow strategy with pause/resume callbacks.
     * 
     * <p>Use this constructor when you need to pause/resume external systems
     * (e.g., Kafka consumer) during backpressure.
     * 
     * @param threshold the backpressure threshold (0.0 to 1.0)
     * @param overflowStorage the storage for overflow items
     * @param backpressureProvider the provider to check for backpressure resolution
     * @param submitFunction function to submit items for replay
     * @param onPause callback to execute when backpressure enters (may be null)
     * @param onResume callback to execute when backpressure resolves (may be null)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public OverflowStrategy(
            double threshold,
            OverflowStorage<T> overflowStorage,
            BackpressureProvider backpressureProvider,
            Function<T, CompletableFuture<com.vajrapulse.vortex.BatchResult<T>>> submitFunction,
            Runnable onPause,
            Runnable onResume) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                "Threshold must be between 0.0 and 1.0, got: " + threshold
            );
        }
        if (overflowStorage == null) {
            throw new IllegalArgumentException("OverflowStorage cannot be null");
        }
        if (backpressureProvider == null) {
            throw new IllegalArgumentException("BackpressureProvider cannot be null");
        }
        if (submitFunction == null) {
            throw new IllegalArgumentException("SubmitFunction cannot be null");
        }
        this.threshold = threshold;
        this.overflowStorage = overflowStorage;
        this.backpressureProvider = backpressureProvider;
        this.submitFunction = submitFunction;
        this.onPause = onPause;
        this.onResume = onResume;
    }
    
    @Override
    public BackpressureResult<T> handle(BackpressureContext<T> context) {
        if (context.backpressureLevel() >= threshold) {
            // Store to overflow
            overflowStorage.add(context.item());
            return BackpressureResult.drop(context.item()); // Drop from normal flow
        }
        return BackpressureResult.accept(context.item());
    }
    
    @Override
    public void onBackpressureEntered(BackpressureProvider provider) {
        if (onPause != null) {
            try {
                onPause.run(); // Pause Kafka consumer (or any other action)
            } catch (Exception e) {
                // Log but don't fail - overflow will still work
                System.err.println("Error executing onPause callback: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void onBackpressureResolved(BackpressureProvider provider) {
        replayOverflowItems();
        if (onResume != null) {
            try {
                onResume.run(); // Resume Kafka consumer (or any other action)
            } catch (Exception e) {
                // Log but don't fail - replay will still work
                System.err.println("Error executing onResume callback: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void onBackpressureActive(BackpressureProvider provider) {
        // Check if we can start replaying (e.g., queue depth < 50% of threshold)
        double level = provider.getBackpressureLevel();
        if (level < threshold * 0.5 && !overflowStorage.isEmpty()) {
            // Start gradual replay
            replayOverflowItems();
        }
    }
    
    private void replayOverflowItems() {
        while (!overflowStorage.isEmpty() && 
               backpressureProvider.getBackpressureLevel() < threshold) {
            T item = overflowStorage.poll();
            if (item != null) {
                try {
                    submitFunction.apply(item);
                } catch (Exception e) {
                    // Log but continue replaying other items
                    System.err.println("Error replaying item: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // If poll() returns null, the storage might be empty now
                // Break to avoid infinite loop if storage reports non-empty but returns null
                break;
            }
        }
    }
}

