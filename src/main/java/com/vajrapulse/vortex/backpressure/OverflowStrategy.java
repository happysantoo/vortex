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
            try {
                overflowStorage.add(context.item());
                return BackpressureResult.drop(context.item()); // Drop from normal flow
            } catch (IllegalStateException e) {
                // Overflow storage is full - reject the item
                // This is better than silently dropping when overflow is full
                Exception reason = new BackpressureException(
                    String.format(
                        "Overflow storage is full. Backpressure level: %.2f (threshold: %.2f, source: %s)",
                        context.backpressureLevel(),
                        threshold,
                        context.provider().getSourceName()
                    ),
                    context.backpressureLevel(),
                    threshold,
                    context.provider().getSourceName()
                );
                return BackpressureResult.reject(context.item(), reason);
            } catch (Exception e) {
                // Unexpected error adding to overflow - reject to be safe
                Exception reason = new BackpressureException(
                    String.format(
                        "Error adding to overflow storage: %s. Backpressure level: %.2f",
                        e.getMessage(),
                        context.backpressureLevel()
                    ),
                    e,
                    context.backpressureLevel(),
                    threshold,
                    context.provider().getSourceName()
                );
                return BackpressureResult.reject(context.item(), reason);
            }
        }
        return BackpressureResult.accept(context.item());
    }
    
    @Override
    public double getThreshold() {
        return threshold;
    }
    
    @Override
    public void onBackpressureEntered(BackpressureProvider provider) {
        if (onPause != null) {
            try {
                onPause.run(); // Pause Kafka consumer (or any other action)
            } catch (Exception e) {
                // Log but don't fail - overflow will still work
                // Use proper logging if available, otherwise System.err
                String errorMsg = "Error executing onPause callback: " + e.getMessage();
                System.err.println(errorMsg);
                if (e.getCause() != null) {
                    System.err.println("Caused by: " + e.getCause().getMessage());
                }
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
                String errorMsg = "Error executing onResume callback: " + e.getMessage();
                System.err.println(errorMsg);
                if (e.getCause() != null) {
                    System.err.println("Caused by: " + e.getCause().getMessage());
                }
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void onBackpressureActive(BackpressureProvider provider) {
        // Check if we can start replaying (e.g., queue depth < 50% of threshold)
        try {
            double level = provider.getBackpressureLevel();
            // Validate level before using
            if (Double.isNaN(level) || level < 0.0 || level > 1.0) {
                return; // Invalid level, skip replay
            }
            if (level < threshold * 0.5 && !overflowStorage.isEmpty()) {
                // Start gradual replay
                replayOverflowItems();
            }
        } catch (Exception e) {
            // Log but don't fail - monitoring will continue
            String errorMsg = "Error in onBackpressureActive: " + e.getMessage();
            System.err.println(errorMsg);
            e.printStackTrace();
        }
    }
    
    private void replayOverflowItems() {
        int maxReplayAttempts = 1000; // Prevent infinite loops
        int attempts = 0;
        int consecutiveNullPolls = 0; // Track consecutive null polls to prevent infinite loops
        final int maxConsecutiveNullPolls = 3; // Max consecutive null polls before giving up
        
        while (attempts < maxReplayAttempts) {
            try {
                // Check if storage is empty before attempting to poll
                if (overflowStorage.isEmpty()) {
                    break; // Storage is empty, we're done
                }
                
                // Check backpressure level
                double level = backpressureProvider.getBackpressureLevel();
                // Validate level
                if (Double.isNaN(level) || level < 0.0 || level > 1.0) {
                    break; // Invalid level, stop replay
                }
                if (level >= threshold) {
                    break; // Backpressure increased, stop replay
                }
                
                T item = overflowStorage.poll();
                if (item != null) {
                    consecutiveNullPolls = 0; // Reset counter on successful poll
                    try {
                        submitFunction.apply(item);
                        attempts = 0; // Reset counter on successful replay
                    } catch (Exception e) {
                        // Log but continue replaying other items
                        String errorMsg = "Error replaying item: " + e.getMessage();
                        System.err.println(errorMsg);
                        if (e.getCause() != null) {
                            System.err.println("Caused by: " + e.getCause().getMessage());
                        }
                        e.printStackTrace();
                        attempts++; // Increment on error
                    }
                } else {
                    // If poll() returns null, increment counter
                    consecutiveNullPolls++;
                    if (consecutiveNullPolls >= maxConsecutiveNullPolls) {
                        // Storage reports non-empty but keeps returning null
                        // This is likely a bug in the storage implementation, but we should exit
                        break;
                    }
                    // Re-check if storage is actually empty now (defensive check)
                    if (overflowStorage.isEmpty()) {
                        break; // Storage is now empty
                    }
                    // If we get here, storage reports non-empty but returned null
                    // This is unusual but could happen in race conditions
                    // Continue to next iteration but with increased null poll counter
                }
            } catch (Exception e) {
                // Error getting backpressure level or other unexpected error
                String errorMsg = "Error during overflow replay: " + e.getMessage();
                System.err.println(errorMsg);
                e.printStackTrace();
                attempts++;
                if (attempts >= maxReplayAttempts) {
                    break; // Too many errors, stop replay
                }
            }
        }
        
        if (attempts >= maxReplayAttempts && !overflowStorage.isEmpty()) {
            System.err.println("Warning: Stopped overflow replay after " + maxReplayAttempts + 
                " attempts. " + overflowStorage.size() + " items remaining in overflow.");
        }
    }
}

