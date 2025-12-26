package com.vajrapulse.vortex.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link MetricsProvider} implementation backed by Micrometer meters.
 *
 * <p>This class is a simple adapter from Micrometer primitives to the
 * {@link MetricsProvider} view used by callers. It intentionally contains no
 * additional logic beyond reading meter values and handling NaN cases.
 */
class DefaultMetricsProvider implements MetricsProvider {

    private final BlockingQueue<?> queue;
    private final Counter requestsSubmitted;
    private final Counter requestsSucceeded;
    private final Counter requestsFailed;
    private final Counter requestsReplayed;
    private final Counter requestsRetried;
    private final Counter requestsRejected;
    private final Counter batchesDispatched;
    private final Timer batchDispatchLatency;
    private final Timer requestWaitLatency;

    DefaultMetricsProvider(
            BlockingQueue<?> queue,
            Counter requestsSubmitted,
            Counter requestsSucceeded,
            Counter requestsFailed,
            Counter requestsReplayed,
            Counter requestsRetried,
            Counter requestsRejected,
            Counter batchesDispatched,
            Timer batchDispatchLatency,
            Timer requestWaitLatency) {

        this.queue = queue;
        this.requestsSubmitted = requestsSubmitted;
        this.requestsSucceeded = requestsSucceeded;
        this.requestsFailed = requestsFailed;
        this.requestsReplayed = requestsReplayed;
        this.requestsRetried = requestsRetried;
        this.requestsRejected = requestsRejected;
        this.batchesDispatched = batchesDispatched;
        this.batchDispatchLatency = batchDispatchLatency;
        this.requestWaitLatency = requestWaitLatency;
    }

    @Override
    public double getFailureRate() {
        double submitted = requestsSubmitted.count();
        if (submitted == 0.0) {
            return 0.0;
        }
        return requestsFailed.count() / submitted;
    }

    @Override
    public double getSuccessRate() {
        double submitted = requestsSubmitted.count();
        if (submitted == 0.0) {
            return 0.0;
        }
        return requestsSucceeded.count() / submitted;
    }

    @Override
    public long getTotalSubmitted() {
        return (long) requestsSubmitted.count();
    }

    @Override
    public long getTotalSucceeded() {
        return (long) requestsSucceeded.count();
    }

    @Override
    public long getTotalFailed() {
        return (long) requestsFailed.count();
    }

    @Override
    public long getTotalReplayed() {
        return (long) requestsReplayed.count();
    }

    @Override
    public long getTotalRetried() {
        return (long) requestsRetried.count();
    }

    @Override
    public long getTotalRejected() {
        return (long) requestsRejected.count();
    }

    @Override
    public int getQueueDepth() {
        return queue.size();
    }

    @Override
    public long getTotalBatchesDispatched() {
        return (long) batchesDispatched.count();
    }

    @Override
    public double getAverageDispatchLatency() {
        long count = batchDispatchLatency.count();
        if (count == 0) {
            return 0.0;
        }
        double mean = batchDispatchLatency.totalTime(TimeUnit.MILLISECONDS) / count;
        return Double.isNaN(mean) ? 0.0 : mean;
    }

    @Override
    public double getAverageWaitLatency() {
        long count = requestWaitLatency.count();
        if (count == 0) {
            return 0.0;
        }
        double mean = requestWaitLatency.totalTime(TimeUnit.MILLISECONDS) / count;
        return Double.isNaN(mean) ? 0.0 : mean;
    }

    @Override
    public double getP95DispatchLatency() {
        // Percentiles are published at timer creation, so percentile() is available
        double percentile = batchDispatchLatency.percentile(0.95, TimeUnit.MILLISECONDS);
        return Double.isNaN(percentile) ? 0.0 : percentile;
    }

    @Override
    public double getP99DispatchLatency() {
        // Percentiles are published at timer creation, so percentile() is available
        double percentile = batchDispatchLatency.percentile(0.99, TimeUnit.MILLISECONDS);
        return Double.isNaN(percentile) ? 0.0 : percentile;
    }
}


