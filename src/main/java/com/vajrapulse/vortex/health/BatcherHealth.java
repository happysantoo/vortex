package com.vajrapulse.vortex.health;

import com.vajrapulse.vortex.MicroBatcher;
import com.vajrapulse.vortex.metrics.MetricsProvider;
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
    
    // Default thresholds for health checks
    private static final double DEFAULT_MAX_FAILURE_RATE = 0.5;
    private static final double DEFAULT_MAX_QUEUE_UTILIZATION = 0.95;
    
    // Degraded thresholds as multipliers of max thresholds
    private static final double DEGRADED_FAILURE_RATE_MULTIPLIER = 0.5;
    private static final double DEGRADED_QUEUE_UTILIZATION_MULTIPLIER = 0.8;
    
    private BatcherHealth() {
        // Utility class - no instantiation
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
        return checkWithThresholds(
            batcher,
            DEFAULT_MAX_FAILURE_RATE,
            DEFAULT_MAX_QUEUE_UTILIZATION
        );
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
        
        // Get metrics
        double failureRate = metrics.getFailureRate();
        int queueDepth = diag.getQueueDepth();
        int maxQueueSize = batcher.getConfig().getMaxQueueSize();
        double queueUtilization = maxQueueSize > 0 
            ? (double) queueDepth / maxQueueSize 
            : 0.0;
        
        // Evaluate health status
        return evaluateHealth(
            failureRate,
            queueUtilization,
            maxFailureRate,
            maxQueueUtilization
        );
    }
    
    /**
     * Evaluates health status based on failure rate and queue utilization.
     * 
     * @param failureRate current failure rate
     * @param queueUtilization current queue utilization
     * @param maxFailureRate maximum acceptable failure rate
     * @param maxQueueUtilization maximum acceptable queue utilization
     * @return the health status
     */
    private static HealthStatus evaluateHealth(
            double failureRate,
            double queueUtilization,
            double maxFailureRate,
            double maxQueueUtilization) {
        
        // Check failure rate thresholds
        if (failureRate > maxFailureRate) {
            return HealthStatus.DOWN;
        } else if (failureRate > maxFailureRate * DEGRADED_FAILURE_RATE_MULTIPLIER) {
            return HealthStatus.DEGRADED;
        }
        
        // Check queue utilization thresholds
        if (queueUtilization > maxQueueUtilization) {
            return HealthStatus.DOWN;
        } else if (queueUtilization > maxQueueUtilization * DEGRADED_QUEUE_UTILIZATION_MULTIPLIER) {
            return HealthStatus.DEGRADED;
        }
        
        return HealthStatus.UP;
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
        int maxQueueSize = batcher.getConfig().getMaxQueueSize();
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
}
