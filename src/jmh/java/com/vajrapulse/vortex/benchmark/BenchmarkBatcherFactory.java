package com.vajrapulse.vortex.benchmark;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.results.SuccessEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating MicroBatcher instances for JMH benchmarks.
 * 
 * <p>This class provides common batcher configurations used across multiple
 * benchmark classes, reducing duplication and ensuring consistency.
 */
public class BenchmarkBatcherFactory {
    
    /**
     * Creates a simple backend that always succeeds (converts all items to SuccessEvent).
     * 
     * @param <T> the type of request elements
     * @return a Backend that always returns successful results
     */
    public static <T> Backend<T> successBackend() {
        return batch -> {
            List<SuccessEvent<T>> successes = new ArrayList<>();
            for (T item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
    }
    
    /**
     * Creates a MicroBatcher with default benchmark configuration (large queue, fast processing).
     * 
     * @param <T> the type of request elements
     * @return a MicroBatcher configured for throughput benchmarks
     */
    public static <T> MicroBatcher<T> defaultBatcher() {
        Backend<T> backend = successBackend();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(10))
            .maxQueueSize(1000)
            .build();
        return new MicroBatcher<>(backend, config, new SimpleMeterRegistry());
    }
    
    /**
     * Creates a MicroBatcher with small queue for rejection testing.
     * 
     * @param <T> the type of request elements
     * @param batchSize the batch size (must be <= maxQueueSize)
     * @param maxQueueSize the maximum queue size (must be >= batchSize)
     * @param rejectionThreshold the queue rejection threshold (0.0 to 1.0)
     * @return a MicroBatcher configured for rejection benchmarks
     */
    public static <T> MicroBatcher<T> smallQueueBatcher(int batchSize, int maxQueueSize, double rejectionThreshold) {
        if (maxQueueSize < batchSize) {
            throw new IllegalArgumentException("maxQueueSize must be >= batchSize");
        }
        Backend<T> backend = successBackend();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(batchSize)
            .lingerTime(Duration.ofMillis(10))
            .maxQueueSize(maxQueueSize)
            .queueRejectionThreshold(rejectionThreshold)
            .build();
        return new MicroBatcher<>(backend, config, new SimpleMeterRegistry());
    }
    
    /**
     * Creates a MicroBatcher optimized for latency benchmarks (small batches, short linger).
     * 
     * @param <T> the type of request elements
     * @return a MicroBatcher configured for latency benchmarks
     */
    public static <T> MicroBatcher<T> latencyBatcher() {
        Backend<T> backend = successBackend();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(10))
            .maxQueueSize(1000)
            .build();
        return new MicroBatcher<>(backend, config, new SimpleMeterRegistry());
    }
}

