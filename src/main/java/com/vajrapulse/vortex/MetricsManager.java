package com.vajrapulse.vortex;

import io.micrometer.core.instrument.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages all metrics for the MicroBatcher.
 * Centralizes metric creation and recording logic.
 */
class MetricsManager {
    private final MeterRegistry meterRegistry;
    private final BatcherConfig config;
    private final BlockingQueue<?> queue;
    
    // Core metrics
    private final Counter requestsSubmitted;
    private final Counter batchesDispatched;
    private final Counter requestsSucceeded;
    private final Counter requestsFailed;
    private final Counter requestsReplayed;
    private final Timer batchDispatchLatency;
    private final Timer requestWaitLatency;
    private final Timer queueWaitTime;
    private final DistributionSummary batchSizeHistogram;
    
    // Per-item metrics (optional)
    private final Timer itemSubmitLatency;
    private final Timer itemWaitTime;
    private final DistributionSummary itemBatchSize;
    
    MetricsManager(MeterRegistry meterRegistry, BatcherConfig config, BlockingQueue<?> queue) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.queue = queue;
        
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
    
    void recordRequestSubmitted() {
        requestsSubmitted.increment();
    }
    
    void recordBatchDispatched() {
        batchesDispatched.increment();
    }
    
    void recordRequestSucceeded() {
        requestsSucceeded.increment();
    }
    
    void recordRequestFailed() {
        requestsFailed.increment();
    }
    
    void recordRequestReplayed() {
        requestsReplayed.increment();
    }
    
    Timer.Sample startBatchDispatchTimer() {
        return Timer.start(meterRegistry);
    }
    
    void recordBatchDispatchLatency(Timer.Sample sample) {
        sample.stop(batchDispatchLatency);
    }
    
    void recordBatchSize(int size) {
        batchSizeHistogram.record(size);
    }
    
    void recordItemBatchSize(int batchSize) {
        if (itemBatchSize != null) {
            itemBatchSize.record(batchSize);
        }
    }
    
    void recordWaitTime(long waitTimeNanos) {
        requestWaitLatency.record(waitTimeNanos, TimeUnit.NANOSECONDS);
        queueWaitTime.record(waitTimeNanos, TimeUnit.NANOSECONDS);
        
        if (config.isPerItemMetrics()) {
            if (itemWaitTime != null) {
                itemWaitTime.record(waitTimeNanos, TimeUnit.NANOSECONDS);
            }
            if (itemSubmitLatency != null) {
                itemSubmitLatency.record(waitTimeNanos, TimeUnit.NANOSECONDS);
            }
        }
    }
    
    /**
     * Creates a MetricsProvider that provides real-time access to batcher metrics.
     * 
     * @return a MetricsProvider instance
     */
    MetricsProvider getMetricsProvider() {
        return new MetricsProvider() {
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
            public int getQueueDepth() {
                return queue.size();
            }
            
            @Override
            public long getTotalBatchesDispatched() {
                return (long) batchesDispatched.count();
            }
            
            @Override
            public double getAverageDispatchLatency() {
                double mean = batchDispatchLatency.mean(TimeUnit.MILLISECONDS);
                return Double.isNaN(mean) ? 0.0 : mean;
            }
            
            @Override
            public double getAverageWaitLatency() {
                double mean = requestWaitLatency.mean(TimeUnit.MILLISECONDS);
                return Double.isNaN(mean) ? 0.0 : mean;
            }
            
            @Override
            public double getP95DispatchLatency() {
                double percentile = batchDispatchLatency.percentile(0.95, TimeUnit.MILLISECONDS);
                return Double.isNaN(percentile) ? 0.0 : percentile;
            }
            
            @Override
            public double getP99DispatchLatency() {
                double percentile = batchDispatchLatency.percentile(0.99, TimeUnit.MILLISECONDS);
                return Double.isNaN(percentile) ? 0.0 : percentile;
            }
        };
    }
}

