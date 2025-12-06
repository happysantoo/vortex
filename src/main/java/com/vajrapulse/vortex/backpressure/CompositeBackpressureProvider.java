package com.vajrapulse.vortex.backpressure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Combines multiple backpressure providers into a single provider.
 * 
 * <p>Uses the maximum backpressure level from all providers (worst-case scenario).
 * This ensures that if any resource is under pressure, the system responds.
 * 
 * <p>Example: Combine connection pool and queue depth backpressure
 * <pre>{@code
 * BackpressureProvider composite = new CompositeBackpressureProvider(
 *     connectionPoolProvider,
 *     queueDepthProvider
 * );
 * }</pre>
 * 
 * <p>Example using builder pattern:
 * <pre>{@code
 * BackpressureProvider composite = CompositeBackpressureProvider.builder()
 *     .queueDepth(() -> batcher.getQueueDepth(), maxQueueSize)
 *     .add(connectionPoolProvider)
 *     .add(customProvider)
 *     .build();
 * }</pre>
 * 
 * <p>The composite provider reports the maximum backpressure level, ensuring
 * that the system responds to pressure from any source. This is a conservative
 * approach that prioritizes system stability.
 * 
 * @since 0.0.4
 */
public class CompositeBackpressureProvider implements BackpressureProvider {
    private final List<BackpressureProvider> providers;
    
    /**
     * Creates a new composite backpressure provider.
     * 
     * @param providers the providers to combine (must not be empty)
     * @throws IllegalArgumentException if no providers are provided
     */
    public CompositeBackpressureProvider(BackpressureProvider... providers) {
        if (providers == null || providers.length == 0) {
            throw new IllegalArgumentException("At least one provider is required");
        }
        // Check for null providers
        for (BackpressureProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Provider cannot be null");
            }
        }
        this.providers = List.of(providers);
    }
    
    /**
     * Creates a new composite backpressure provider from a list.
     * 
     * @param providers the providers to combine (must not be empty)
     * @throws IllegalArgumentException if no providers are provided
     */
    private CompositeBackpressureProvider(List<BackpressureProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider is required");
        }
        // Check for null providers
        for (BackpressureProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Provider cannot be null");
            }
        }
        this.providers = List.copyOf(providers);
    }
    
    /**
     * Creates a new builder for constructing a composite backpressure provider.
     * 
     * <p>The builder provides a fluent API for combining multiple backpressure providers,
     * including convenience methods for common providers like queue depth.
     * 
     * <p>Example:
     * <pre>{@code
     * BackpressureProvider composite = CompositeBackpressureProvider.builder()
     *     .queueDepth(() -> batcher.getQueueDepth(), 1000)
     *     .add(connectionPoolProvider)
     *     .build();
     * }</pre>
     * 
     * @return a new builder instance
     * @since 0.0.7
     */
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public double getBackpressureLevel() {
        return providers.stream()
            .mapToDouble(BackpressureProvider::getBackpressureLevel)
            .max()
            .orElse(0.0);
    }
    
    @Override
    public String getSourceName() {
        return "Composite (" + providers.size() + " sources)";
    }
    
    @Override
    public Map<String, Object> getDetails() {
        Map<String, Object> details = new java.util.HashMap<>();
        details.put("maxBackpressure", getBackpressureLevel());
        details.put("providerCount", providers.size());
        
        // Add per-provider details
        for (int i = 0; i < providers.size(); i++) {
            BackpressureProvider provider = providers.get(i);
            String prefix = "provider" + i + ".";
            details.put(prefix + "name", provider.getSourceName());
            details.put(prefix + "level", provider.getBackpressureLevel());
            provider.getDetails().forEach((key, value) ->
                details.put(prefix + key, value)
            );
        }
        
        return Map.copyOf(details);
    }
    
    /**
     * Builder for constructing composite backpressure providers.
     * 
     * <p>Provides a fluent API for combining multiple backpressure providers.
     * 
     * <p>Example:
     * <pre>{@code
     * BackpressureProvider composite = CompositeBackpressureProvider.builder()
     *     .queueDepth(() -> batcher.getQueueDepth(), 1000)
     *     .add(connectionPoolProvider)
     *     .add(customProvider)
     *     .build();
     * }</pre>
     * 
     * @since 0.0.7
     */
    public static class Builder {
        private final List<BackpressureProvider> providers = new ArrayList<>();
        
        /**
         * Creates a new builder instance.
         */
        private Builder() {
        }
        
        /**
         * Adds a queue depth backpressure provider.
         * 
         * <p>This is a convenience method for adding a {@link QueueDepthBackpressureProvider}
         * to the composite. The provider monitors queue depth and reports backpressure
         * based on how full the queue is.
         * 
         * <p>Example:
         * <pre>{@code
         * Builder builder = CompositeBackpressureProvider.builder()
         *     .queueDepth(() -> batcher.getQueueDepth(), 1000);
         * }</pre>
         * 
         * @param queueDepthSupplier supplier that returns the current queue depth
         * @param maxQueueSize the maximum queue size (used to calculate backpressure level)
         * @return this builder instance
         * @throws IllegalArgumentException if queueDepthSupplier is null or maxQueueSize is not positive
         * @since 0.0.7
         */
        public Builder queueDepth(Supplier<Integer> queueDepthSupplier, int maxQueueSize) {
            if (queueDepthSupplier == null) {
                throw new IllegalArgumentException("Queue depth supplier cannot be null");
            }
            if (maxQueueSize <= 0) {
                throw new IllegalArgumentException("Max queue size must be positive");
            }
            providers.add(new QueueDepthBackpressureProvider(queueDepthSupplier, maxQueueSize));
            return this;
        }
        
        /**
         * Adds a backpressure provider to the composite.
         * 
         * <p>Multiple providers can be added. The composite will report the maximum
         * backpressure level from all providers.
         * 
         * <p>Example:
         * <pre>{@code
         * Builder builder = CompositeBackpressureProvider.builder()
         *     .add(connectionPoolProvider)
         *     .add(customProvider);
         * }</pre>
         * 
         * @param provider the backpressure provider to add
         * @return this builder instance
         * @throws IllegalArgumentException if provider is null
         * @since 0.0.7
         */
        public Builder add(BackpressureProvider provider) {
            if (provider == null) {
                throw new IllegalArgumentException("Provider cannot be null");
            }
            providers.add(provider);
            return this;
        }
        
        /**
         * Builds the composite backpressure provider.
         * 
         * <p>At least one provider must be added before building.
         * 
         * @return a new CompositeBackpressureProvider instance
         * @throws IllegalArgumentException if no providers were added
         * @since 0.0.7
         */
        public CompositeBackpressureProvider build() {
            if (providers.isEmpty()) {
                throw new IllegalArgumentException("At least one provider is required");
            }
            return new CompositeBackpressureProvider(providers);
        }
    }
}

