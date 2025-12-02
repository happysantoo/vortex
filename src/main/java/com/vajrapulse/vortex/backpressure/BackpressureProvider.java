package com.vajrapulse.vortex.backpressure;

import java.util.Map;

/**
 * Provides backpressure level from a system resource.
 * 
 * <p>Backpressure is reported on a scale of 0.0 to 1.0:
 * <ul>
 *   <li>0.0 - 0.3: Low pressure, system can accept more load</li>
 *   <li>0.3 - 0.7: Moderate pressure, system is approaching capacity</li>
 *   <li>0.7 - 1.0: High pressure, system is overloaded</li>
 * </ul>
 * 
 * <p>Implementations should be thread-safe and fast (avoid blocking operations).
 * This method may be called frequently from multiple threads concurrently.
 * 
 * <p>Example implementations:
 * <ul>
 *   <li>Queue depth-based backpressure</li>
 *   <li>Connection pool utilization</li>
 *   <li>Memory pressure</li>
 *   <li>CPU utilization</li>
 * </ul>
 */
public interface BackpressureProvider {
    /**
     * Gets the current backpressure level.
     * 
     * <p>This method must be thread-safe and should return quickly (ideally &lt; 1ms).
     * Avoid blocking operations or expensive computations.
     * 
     * @return backpressure level from 0.0 (no pressure) to 1.0 (maximum pressure)
     */
    double getBackpressureLevel();
    
    /**
     * Gets a human-readable name for this backpressure source.
     * 
     * <p>Used for logging, metrics, and error messages to identify which
     * resource is causing backpressure.
     * 
     * @return source name (e.g., "Vortex Queue Depth", "HikariCP Connection Pool")
     */
    String getSourceName();
    
    /**
     * Gets optional details about the current backpressure state.
     * 
     * <p>Useful for debugging and monitoring. May return empty map if no details available.
     * Values should be simple types (String, Number, Boolean) for easy serialization.
     * 
     * <p>Example details:
     * <ul>
     *   <li>Queue depth: {"queueDepth": 50, "maxCapacity": 100}</li>
     *   <li>Connection pool: {"active": 10, "total": 20, "waiting": 5}</li>
     * </ul>
     * 
     * @return map of detail key-value pairs, or empty map if no details available
     */
    default Map<String, Object> getDetails() {
        return Map.of();
    }
}

