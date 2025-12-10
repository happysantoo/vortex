package com.vajrapulse.vortex;

/**
 * Detailed health information for a MicroBatcher instance.
 * 
 * <p>Provides comprehensive health metrics and status information
 * that can be used for monitoring, alerting, and dashboards.
 * 
 * @param status overall health status
 * @param closed whether the batcher is closed
 * @param failureRate current failure rate (0.0 to 1.0)
 * @param successRate current success rate (0.0 to 1.0)
 * @param queueDepth current queue depth
 * @param maxQueueSize maximum queue size
 * @param queueUtilization queue utilization (0.0 to 1.0)
 * @param totalSubmitted total requests submitted
 * @param totalSucceeded total requests succeeded
 * @param totalFailed total requests failed
 * 
 * @since 0.0.5
 */
public record HealthInfo(
    HealthStatus status,
    boolean closed,
    double failureRate,
    double successRate,
    int queueDepth,
    int maxQueueSize,
    double queueUtilization,
    long totalSubmitted,
    long totalSucceeded,
    long totalFailed
) {
    /**
     * Checks if the batcher is healthy (UP status).
     * 
     * @return true if healthy, false otherwise
     */
    public boolean isHealthy() {
        return status == HealthStatus.UP;
    }
    
    /**
     * Checks if the batcher is in a degraded state.
     * 
     * @return true if degraded, false otherwise
     */
    public boolean isDegraded() {
        return status == HealthStatus.DEGRADED;
    }
    
    /**
     * Checks if the batcher is down.
     * 
     * @return true if down, false otherwise
     */
    public boolean isDown() {
        return status == HealthStatus.DOWN;
    }
}

