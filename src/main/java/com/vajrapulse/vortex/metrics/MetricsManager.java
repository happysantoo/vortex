package com.vajrapulse.vortex.metrics;

import com.vajrapulse.vortex.BatcherConfig;
import io.micrometer.core.instrument.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages all metrics for the MicroBatcher.
 * Centralizes metric creation and recording logic.
 */
public class MetricsManager {
    private final MeterRegistry meterRegistry;
    private final BatcherConfig config;
    private final BlockingQueue<?> queue;
    private final boolean perItemMetricsEnabled;

    // Core metrics
    private final Counter requestsSubmitted;
    private final Counter batchesDispatched;
    private final Counter requestsSucceeded;
    private final Counter requestsFailed;
    private final Counter requestsReplayed;
    private final Counter requestsRetried;
    private final Counter requestsRejected;
    private final Counter queueOfferFailures;
    private final Counter dispatchRejected;
    private final Timer batchDispatchLatency;
    private final Timer requestWaitLatency;
    private final Timer queueWaitTime;
    private final DistributionSummary batchSizeHistogram;
    
    // Per-item metrics (optional)
    private final Timer itemSubmitLatency;
    private final Timer itemWaitTime;
    private final DistributionSummary itemBatchSize;
    
    public MetricsManager(MeterRegistry meterRegistry, BatcherConfig config, BlockingQueue<?> queue) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.queue = queue;
        this.perItemMetricsEnabled = config.isPerItemMetrics();
        
        // Initialize core metrics
        this.requestsSubmitted = Counter.builder("vortex.requests.submitted")
            .description("Total number of requests submitted")
            .register(meterRegistry);
        
        this.batchesDispatched = Counter.builder("vortex.batches.dispatched")
            .description("Total number of batches dispatched")
            .register(meterRegistry);
        
        this.requestsSucceeded = Counter.builder("vortex.requests.succeeded")
            .description("Total number of requests that succeeded")
            .register(meterRegistry);
        
        this.requestsFailed = Counter.builder("vortex.requests.failed")
            .description("Total number of requests that failed")
            .register(meterRegistry);
        
        this.requestsReplayed = Counter.builder("vortex.requests.replayed")
            .description("Total number of successful requests that were replayed")
            .register(meterRegistry);
        
        this.requestsRetried = Counter.builder("vortex.requests.retried")
            .description("Total number of requests that were retried")
            .register(meterRegistry);
        
        this.requestsRejected = Counter.builder("vortex.requests.rejected")
            .description("Total number of requests rejected due to queue being full")
            .register(meterRegistry);
        
        this.queueOfferFailures = Counter.builder("vortex.queue.offer.failures")
            .description("Total number of queue offer failures (race condition occurrences)")
            .register(meterRegistry);
        
        this.dispatchRejected = Counter.builder("vortex.dispatch.rejected")
            .description("Number of batches rejected due to concurrent dispatch limit")
            .register(meterRegistry);
        
        this.batchDispatchLatency = Timer.builder("vortex.batch.dispatch.latency")
            .description("Time taken to dispatch a batch")
            .register(meterRegistry);
        
        this.requestWaitLatency = Timer.builder("vortex.request.wait.latency")
            .description("Time a request waits before being batched")
            .register(meterRegistry);
        
        this.queueWaitTime = Timer.builder("vortex.queue.wait.time")
            .description("Distribution of queue wait times")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        this.batchSizeHistogram = DistributionSummary.builder("vortex.batch.size")
            .description("Distribution of batch sizes")
            .register(meterRegistry);
        
        // Initialize per-item metrics if enabled
        if (config.isPerItemMetrics()) {
            this.itemSubmitLatency = Timer.builder("vortex.item.submit.latency")
                .description("Time from submit to batch completion (per item)")
                .register(meterRegistry);
            this.itemWaitTime = Timer.builder("vortex.item.wait.time")
                .description("Time item waits in queue before batching")
                .register(meterRegistry);
            this.itemBatchSize = DistributionSummary.builder("vortex.item.batch.size")
                .description("Size of batch when item was processed")
                .register(meterRegistry);
        } else {
            this.itemSubmitLatency = null;
            this.itemWaitTime = null;
            this.itemBatchSize = null;
        }
        
        // Register queue depth gauge (use queue.size() directly - optimization: no redundant tracking)
        Gauge.builder("vortex.queue.depth", queue, BlockingQueue::size)
            .description("Current depth of the request queue")
            .register(meterRegistry);
    }
    
    public void recordRequestSubmitted() {
        requestsSubmitted.increment();
    }
    
    public void recordBatchDispatched() {
        batchesDispatched.increment();
    }
    
    public void recordRequestSucceeded() {
        requestsSucceeded.increment();
    }
    
    public void recordRequestFailed() {
        requestsFailed.increment();
    }
    
    public void recordRequestReplayed() {
        requestsReplayed.increment();
    }
    
    public void recordRequestRetried() {
        requestsRetried.increment();
    }
    
    public void recordRequestRejected() {
        requestsRejected.increment();
    }
    
    public void recordQueueOfferFailure() {
        queueOfferFailures.increment();
    }
    
    public void recordDispatchRejected() {
        dispatchRejected.increment();
    }
    
    public Timer.Sample startBatchDispatchTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordBatchDispatchLatency(Timer.Sample sample) {
        sample.stop(batchDispatchLatency);
    }
    
    public void recordBatchSize(int size) {
        batchSizeHistogram.record(size);
    }
    
    public void recordItemBatchSize(int batchSize) {
        if (perItemMetricsEnabled && itemBatchSize != null) {
            itemBatchSize.record(batchSize);
        }
    }
    
    public void recordWaitTime(long waitTimeNanos) {
        requestWaitLatency.record(waitTimeNanos, TimeUnit.NANOSECONDS);
        queueWaitTime.record(waitTimeNanos, TimeUnit.NANOSECONDS);
    }
    
    /**
     * Records queue wait time for an individual item (from submit to batch dispatch start).
     * Only records if per-item metrics are enabled.
     * 
     * @param queueWaitTimeNanos the queue wait time in nanoseconds
     */
    public void recordQueueWaitTime(long queueWaitTimeNanos) {
        if (perItemMetricsEnabled && itemWaitTime != null) {
            itemWaitTime.record(queueWaitTimeNanos, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Records full submit-to-completion latency for an individual item.
     * Only records if per-item metrics are enabled.
     * 
     * @param fullLatencyNanos the full latency from submit to completion in nanoseconds
     */
    public void recordItemSubmitLatency(long fullLatencyNanos) {
        if (perItemMetricsEnabled && itemSubmitLatency != null) {
            itemSubmitLatency.record(fullLatencyNanos, TimeUnit.NANOSECONDS);
        }
    }
    
    /**
     * Creates a MetricsProvider that provides real-time access to batcher metrics.
     * 
     * @return a MetricsProvider instance
     */
    public MetricsProvider getMetricsProvider() {
        return new DefaultMetricsProvider(
            queue,
            requestsSubmitted,
            requestsSucceeded,
            requestsFailed,
            requestsReplayed,
            requestsRetried,
            requestsRejected,
            batchesDispatched,
            batchDispatchLatency,
            requestWaitLatency
        );
    }
}

