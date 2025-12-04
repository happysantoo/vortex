package com.vajrapulse.vortex.backpressure;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple thread-safe cache for backpressure levels with TTL.
 * 
 * <p>This cache stores the last known backpressure level along with its timestamp.
 * If the cached value is still valid (within TTL), it is returned without
 * calling the provider. Otherwise, the provider is called and the cache is updated.
 * 
 * <p>This reduces the overhead of calling {@code getBackpressureLevel()} on every
 * submission, which can be expensive if the provider performs I/O or complex calculations.
 * 
 * <p>Thread safety: This class is thread-safe and can be used concurrently from multiple threads.
 * 
 * @since 0.0.5
 */
public class BackpressureLevelCache {
    private final BackpressureProvider provider;
    private final long ttlNanos;
    private final AtomicReference<CachedValue> cache = new AtomicReference<>();
    
    /**
     * Creates a new cache with the specified provider and TTL.
     * 
     * @param provider the backpressure provider to cache
     * @param ttl the time-to-live for cached values
     */
    public BackpressureLevelCache(BackpressureProvider provider, Duration ttl) {
        this.provider = provider;
        this.ttlNanos = ttl.toNanos();
    }
    
    /**
     * Gets the backpressure level, using cache if valid, otherwise fetching from provider.
     * 
     * @return the backpressure level (0.0 to 1.0)
     */
    public double getBackpressureLevel() {
        CachedValue cached = cache.get();
        long now = System.nanoTime();
        
        // Check if cached value is still valid
        if (cached != null && (now - cached.timestampNanos) < ttlNanos) {
            return cached.level;
        }
        
        // Cache expired or not present - fetch from provider
        double level = provider.getBackpressureLevel();
        
        // Update cache (optimistic - may race, but that's OK)
        cache.set(new CachedValue(level, now));
        
        return level;
    }
    
    /**
     * Invalidates the cache, forcing the next call to fetch from provider.
     */
    public void invalidate() {
        cache.set(null);
    }
    
    /**
     * Cached value with timestamp.
     */
    private static class CachedValue {
        final double level;
        final long timestampNanos;
        
        CachedValue(double level, long timestampNanos) {
            this.level = level;
            this.timestampNanos = timestampNanos;
        }
    }
}

