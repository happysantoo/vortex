package com.vajrapulse.vortex.backpressure;

import java.util.List;
import java.util.Map;

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
 * <p>The composite provider reports the maximum backpressure level, ensuring
 * that the system responds to pressure from any source. This is a conservative
 * approach that prioritizes system stability.
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
}

