package com.vajrapulse.vortex;

/**
 * Health status values for MicroBatcher instances.
 * 
 * <p>Used by {@link BatcherHealth} to indicate the operational state
 * of a MicroBatcher instance.
 * 
 * @since 0.0.5
 */
public enum HealthStatus {
    /**
     * Batcher is healthy and operating normally.
     */
    UP,
    
    /**
     * Batcher is operating but in a degraded state.
     * May indicate performance issues or approaching limits.
     */
    DEGRADED,
    
    /**
     * Batcher is down or critically unhealthy.
     * Immediate attention required.
     */
    DOWN
}

