package com.vajrapulse.vortex;

import com.vajrapulse.vortex.backpressure.BackpressureProvider;
import com.vajrapulse.vortex.backpressure.BackpressureStrategy;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark to measure the overhead of submitSync() compared to submit().
 * 
 * This benchmark helps verify that submitSync() has minimal overhead
 * when items are accepted, and measures the cost of synchronous rejection checks.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SubmitSyncBenchmark {
    
    private MicroBatcher<String> batcher;
    private MicroBatcher<String> batcherWithBackpressure;
    private BackpressureProvider backpressureProvider;
    private BackpressureStrategy<String> backpressureStrategy;
    
    @Setup
    public void setup() {
        // Simple backend that processes immediately
        Backend<String> backend = batch -> {
            var successes = batch.stream()
                .map(SuccessEvent<String>::new)
                .toList();
            return new BatchResult<>(successes, java.util.List.of());
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(1000) // Large queue to avoid rejections
            .build();
        
        this.batcher = new MicroBatcher<>(backend, config);
        
        // Setup backpressure (low backpressure - items will be accepted)
        this.backpressureProvider = new BackpressureProvider() {
            @Override
            public double getBackpressureLevel() {
                return 0.1; // Low backpressure
            }
            
            @Override
            public String getSourceName() {
                return "Benchmark Provider";
            }
        };
        this.backpressureStrategy = new com.vajrapulse.vortex.backpressure.RejectStrategy<>(0.7);
        
        BatcherConfig configWithBackpressure = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(1000)
            .build();
        
        this.batcherWithBackpressure = MicroBatcher.withBackpressure(
            backend, configWithBackpressure, backpressureProvider, backpressureStrategy
        );
    }
    
    @TearDown
    public void tearDown() {
        if (batcher != null) {
            batcher.close();
        }
        if (batcherWithBackpressure != null) {
            batcherWithBackpressure.close();
        }
    }
    
    /**
     * Benchmark submit() - baseline for comparison.
     */
    @Benchmark
    public void submit(Blackhole bh) {
        var future = batcher.submit("item");
        bh.consume(future);
    }
    
    /**
     * Benchmark submitSync() when item is accepted (success path).
     */
    @Benchmark
    public void submitSyncAccepted(Blackhole bh) {
        var result = batcher.submitSync("item");
        bh.consume(result);
    }
    
    /**
     * Benchmark submitSync() with backpressure check (low backpressure - accepted).
     */
    @Benchmark
    public void submitSyncWithBackpressure(Blackhole bh) {
        var result = batcherWithBackpressure.submitSync("item");
        bh.consume(result);
    }
    
    /**
     * Benchmark submitSync() when queue is full (rejection path).
     * Note: This is a best-effort benchmark - queue may not always be full.
     */
    @Benchmark
    public void submitSyncRejected(Blackhole bh) {
        // Try to fill queue first (may not always succeed due to timing)
        for (int i = 0; i < 100; i++) {
            batcher.submitSync("item-" + i);
        }
        var result = batcher.submitSync("item-rejected");
        bh.consume(result);
    }
}

