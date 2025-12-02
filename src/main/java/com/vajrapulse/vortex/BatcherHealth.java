package com.vajrapulse.vortex;

import java.util.Objects;

/**
 * Utility class for health checks on MicroBatcher instances.
 * 
 * <p>Provides standardized health status checks that can be used with
 * health monitoring systems like Spring Actuator, Kubernetes probes, etc.
 * 
 * <p>Example usage:
 * <pre>{@code
 * HealthStatus status = BatcherHealth.check(batcher);
 * if (status == HealthStatus.DOWN) {
 *     // Take action
 * }
 * 
 * // With custom thresholds
 * HealthStatus status = BatcherHealth.checkWithThresholds(
 *     batcher,
 *     0.1,  // max failure rate
 *     0.8   // max queue utilization
 * );
 * }</pre>
 * 
 * @since 0.0.5
 */
public final class BatcherHealth {
    
    private BatcherHealth() {
        // Utility class - no instantiation
    }
    
    /**
     * Health status values.
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
    
    /**
     * Performs a health check on the given MicroBatcher using default thresholds.
     * 
     * <p>Default thresholds:
     * <ul>
     *   <li>Failure rate: &gt; 50% = DOWN, &gt; 10% = DEGRADED</li>
     *   <li>Queue utilization: &gt; 95% = DOWN, &gt; 80% = DEGRADED</li>
     * </ul>
     * 
     * <p>Health status determination:
     * <ul>
     *   <li><b>DOWN</b>: Batcher is closed, failure rate &gt; 50%, or queue utilization &gt; 95%</li>
     *   <li><b>DEGRADED</b>: Failure rate &gt; 10% or queue utilization &gt; 80%</li>
     *   <li><b>UP</b>: Otherwise healthy</li>
     * </ul>
     * 
     * @param batcher the MicroBatcher to check
     * @return the health status
     * @throws NullPointerException if batcher is null
     */
    public static HealthStatus check(MicroBatcher<?> batcher) {
        Objects.requireNonNull(batcher, "Batcher cannot be null");
        
        BatcherDiagnostics diag = batcher.diagnostics();
        MetricsProvider metrics = batcher.getMetricsProvider();
        
        // Check if closed
        if (diag.isClosed()) {
            return HealthStatus.DOWN;
        }
        
        // Check failure rate
        double failureRate = metrics.getFailureRate();
        if (failureRate > 0.5) {
            return HealthStatus.DOWN;
        } else if (failureRate > 0.1) {
            return HealthStatus.DEGRADED;
        }
        
        // Check queue utilization
        int queueDepth = diag.getQueueDepth();
        int maxQueueSize = batcher.getConfig().getMaxQueueSize();
        double queueUtilization = maxQueueSize > 0 
            ? (double) queueDepth / maxQueueSize 
            : 0.0;
        
        if (queueUtilization > 0.95) {
            return HealthStatus.DOWN;
        } else if (queueUtilization > 0.8) {
            return HealthStatus.DEGRADED;
        }
        
        return HealthStatus.UP;
    }
    
    /**
     * Performs a health check with custom thresholds.
     * 
     * <p>Health status determination:
     * <ul>
     *   <li><b>DOWN</b>: Batcher is closed, failure rate &gt; maxFailureRate, or queue utilization &gt; maxQueueUtilization</li>
     *   <li><b>DEGRADED</b>: Failure rate &gt; maxFailureRate * 0.5 or queue utilization &gt; maxQueueUtilization * 0.8</li>
     *   <li><b>UP</b>: Otherwise healthy</li>
     * </ul>
     * 
     * @param batcher the MicroBatcher to check
     * @param maxFailureRate maximum acceptable failure rate (0.0 to 1.0)
     * @param maxQueueUtilization maximum acceptable queue utilization (0.0 to 1.0)
     * @return the health status
     * @throws NullPointerException if batcher is null
     * @throws IllegalArgumentException if thresholds are out of valid range
     */
    public static HealthStatus checkWithThresholds(
            MicroBatcher<?> batcher,
            double maxFailureRate,
            double maxQueueUtilization) {
        Objects.requireNonNull(batcher, "Batcher cannot be null");
        
        if (maxFailureRate < 0.0 || maxFailureRate > 1.0) {
            throw new IllegalArgumentException(
                "maxFailureRate must be between 0.0 and 1.0, got: " + maxFailureRate
            );
        }
        if (maxQueueUtilization < 0.0 || maxQueueUtilization > 1.0) {
            throw new IllegalArgumentException(
                "maxQueueUtilization must be between 0.0 and 1.0, got: " + maxQueueUtilization
            );
        }
        
        BatcherDiagnostics diag = batcher.diagnostics();
        MetricsProvider metrics = batcher.getMetricsProvider();
        
        // Check if closed
        if (diag.isClosed()) {
            return HealthStatus.DOWN;
        }
        
        // Check failure rate
        double failureRate = metrics.getFailureRate();
        if (failureRate > maxFailureRate) {
            return HealthStatus.DOWN;
        } else if (failureRate > maxFailureRate * 0.5) {
            return HealthStatus.DEGRADED;
        }
        
        // Check queue utilization
        int queueDepth = diag.getQueueDepth();
        int maxQueueSize = getMaxQueueSize(batcher);
        double queueUtilization = maxQueueSize > 0 
            ? (double) queueDepth / maxQueueSize 
            : 0.0;
        
        if (queueUtilization > maxQueueUtilization) {
            return HealthStatus.DOWN;
        } else if (queueUtilization > maxQueueUtilization * 0.8) {
            return HealthStatus.DEGRADED;
        }
        
        return HealthStatus.UP;
    }
    
    /**
     * Gets the maximum queue size from the batcher.
     */
    private static int getMaxQueueSize(MicroBatcher<?> batcher) {
        return batcher.getConfig().getMaxQueueSize();
    }
    
    /**
     * Gets detailed health information including metrics and diagnostics.
     * 
     * <p>This method provides a comprehensive health report that includes:
     * <ul>
     *   <li>Overall health status</li>
     *   <li>Failure rate</li>
     *   <li>Queue depth and utilization</li>
     *   <li>Total submitted/succeeded/failed counts</li>
     * </ul>
     * 
     * @param batcher the MicroBatcher to check
     * @return detailed health information
     * @throws NullPointerException if batcher is null
     */
    public static HealthInfo getHealthInfo(MicroBatcher<?> batcher) {
        Objects.requireNonNull(batcher, "Batcher cannot be null");
        
        BatcherDiagnostics diag = batcher.diagnostics();
        MetricsProvider metrics = batcher.getMetricsProvider();
        
        HealthStatus status = check(batcher);
        
        int queueDepth = diag.getQueueDepth();
        int maxQueueSize = getMaxQueueSize(batcher);
        double queueUtilization = maxQueueSize > 0 
            ? (double) queueDepth / maxQueueSize 
            : 0.0;
        
        return new HealthInfo(
            status,
            diag.isClosed(),
            metrics.getFailureRate(),
            metrics.getSuccessRate(),
            queueDepth,
            maxQueueSize,
            queueUtilization,
            metrics.getTotalSubmitted(),
            metrics.getTotalSucceeded(),
            metrics.getTotalFailed()
        );
    }
    
    /**
     * Detailed health information record.
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
}

