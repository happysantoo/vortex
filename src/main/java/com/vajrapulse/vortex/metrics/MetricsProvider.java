package com.vajrapulse.vortex.metrics;

/**
 * Provides real-time metrics from a MicroBatcher instance.
 * 
 * <p>This interface provides convenient access to key metrics for monitoring,
 * adaptive behavior, and decision-making. All metrics are calculated in real-time
 * from the underlying Micrometer metrics.
 * 
 * <p>Use cases:
 * <ul>
 *   <li>Adaptive batch sizing based on failure rate</li>
 *   <li>Circuit breaker patterns</li>
 *   <li>Auto-scaling decisions</li>
 *   <li>Health monitoring</li>
 *   <li>Performance analysis</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * MetricsProvider metrics = batcher.getMetricsProvider();
 * 
 * // Adaptive batch sizing
 * if (metrics.getFailureRate() > 0.1) {
 *     batcher.updateBatchSize(5); // Reduce batch size
 * } else if (metrics.getFailureRate() < 0.01) {
 *     batcher.updateBatchSize(20); // Increase batch size
 * }
 * 
 * // Circuit breaker
 * if (metrics.getFailureRate() > 0.5) {
 *     circuitBreaker.open();
 * }
 * 
 * // Health check
 * boolean isHealthy = metrics.getFailureRate() < 0.05 
 *     && metrics.getQueueDepth() < 100;
 * }</pre>
 * 
 * @since 0.0.3
 */
public interface MetricsProvider {
    
    /**
     * Returns the current failure rate as a value between 0.0 and 1.0.
     * 
     * <p>Calculated as: {@code totalFailed / totalSubmitted}
     * 
     * <p>If no requests have been submitted yet, returns 0.0.
     * 
     * @return failure rate (0.0 = no failures, 1.0 = all failures)
     */
    double getFailureRate();
    
    /**
     * Returns the current success rate as a value between 0.0 and 1.0.
     * 
     * <p>Calculated as: {@code totalSucceeded / totalSubmitted}
     * 
     * <p>If no requests have been submitted yet, returns 0.0.
     * 
     * @return success rate (0.0 = no successes, 1.0 = all successes)
     */
    double getSuccessRate();
    
    /**
     * Returns the total number of requests submitted since the batcher was created.
     * 
     * @return total number of submitted requests
     */
    long getTotalSubmitted();
    
    /**
     * Returns the total number of requests that succeeded.
     * 
     * @return total number of successful requests
     */
    long getTotalSucceeded();
    
    /**
     * Returns the total number of requests that failed.
     * 
     * @return total number of failed requests
     */
    long getTotalFailed();
    
    /**
     * Returns the total number of successful requests that were replayed.
     * 
     * <p>Replay occurs when auto-replay is enabled and a batch contains
     * both successes and failures.
     * 
     * @return total number of replayed requests
     */
            long getTotalReplayed();
    
    /**
     * Returns the current depth of the request queue.
     * 
     * <p>This is the number of items currently waiting to be batched.
     * A high queue depth may indicate backpressure or slow backend processing.
     * 
     * @return current queue depth (0 = empty queue)
     */
    int getQueueDepth();
    
    /**
     * Returns the total number of batches dispatched.
     * 
     * @return total number of batches dispatched
     */
            long getTotalBatchesDispatched();

            /**
             * Returns the total number of requests that were retried.
             *
             * <p>This includes all retry attempts scheduled due to transient
             * failures that matched the configured retry predicate.
             *
             * @return total number of retried requests
             * @since 0.0.3
             */
            long getTotalRetried();

            /**
             * Returns the total number of requests that were rejected due to backpressure.
             *
             * <p>Rejections occur when the internal queue is full (reached
             * {@code maxQueueSize}) and {@code submit()} cannot enqueue the
             * request within the configured offer timeout.
             *
             * @return total number of rejected requests
             * @since 0.0.3
             */
            long getTotalRejected();
    
    /**
     * Returns the average batch dispatch latency in milliseconds.
     * 
     * <p>This is the average time taken to dispatch a batch to the backend.
     * 
     * <p>If no batches have been dispatched yet, returns 0.0.
     * 
     * @return average dispatch latency in milliseconds
     */
    double getAverageDispatchLatency();
    
    /**
     * Returns the average request wait latency in milliseconds.
     * 
     * <p>This is the average time a request waits in the queue before being batched.
     * 
     * <p>If no requests have been processed yet, returns 0.0.
     * 
     * @return average wait latency in milliseconds
     */
    double getAverageWaitLatency();
    
    /**
     * Returns the p95 percentile of batch dispatch latency in milliseconds.
     * 
     * <p>95% of batch dispatches complete within this time.
     * 
     * <p>If no batches have been dispatched yet, returns 0.0.
     * 
     * @return p95 dispatch latency in milliseconds
     */
    double getP95DispatchLatency();
    
    /**
     * Returns the p99 percentile of batch dispatch latency in milliseconds.
     * 
     * <p>99% of batch dispatches complete within this time.
     * 
     * <p>If no batches have been dispatched yet, returns 0.0.
     * 
     * @return p99 dispatch latency in milliseconds
     */
    double getP99DispatchLatency();
}

