package com.vajrapulse.vortex;

import com.vajrapulse.vortex.benchmark.BenchmarkBatcherFactory;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Benchmark to measure the performance of the unified submit() method.
 * 
 * This benchmark measures:
 * - Overhead of submit() when items are accepted
 * - Cost of immediate rejection checks (queue full)
 * - Performance with and without callbacks
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class SubmitSyncBenchmark {
    
    private MicroBatcher<String> batcher;
    private MicroBatcher<String> batcherSmallQueue;
    
    @Setup
    public void setup() {
        // Large queue to avoid rejections
        this.batcher = BenchmarkBatcherFactory.defaultBatcher();
        
        // Small queue to test rejection path
        this.batcherSmallQueue = BenchmarkBatcherFactory.smallQueueBatcher(5, 5, 1.0);
    }
    
    @TearDown
    public void tearDown() {
        if (batcher != null) {
            batcher.close();
        }
        if (batcherSmallQueue != null) {
            batcherSmallQueue.close();
        }
    }
    
    /**
     * Benchmark submit() without callback - baseline performance.
     */
    @Benchmark
    public void submitWithoutCallback(Blackhole bh) {
        var result = batcher.submit("item");
        bh.consume(result);
    }
    
    /**
     * Benchmark submit() with callback - measures callback overhead.
     */
    @Benchmark
    public void submitWithCallback(Blackhole bh) {
        var result = batcher.submit("item", itemResult -> {
            // Callback invoked when item is processed
            bh.consume(itemResult);
        });
        bh.consume(result);
    }
    
    /**
     * Benchmark submit() rejection path - when queue is full.
     * Note: This is a best-effort benchmark - queue may not always be full.
     */
    @Benchmark
    public void submitRejected(Blackhole bh) {
        // Fill small queue to trigger rejections
        for (int i = 0; i < 10; i++) {
            var result = batcherSmallQueue.submit("item-" + i);
            bh.consume(result);
        }
        // This should be rejected
        var result = batcherSmallQueue.submit("item-rejected");
        bh.consume(result);
    }
    
    /**
     * Benchmark submit() with queue rejection threshold check.
     * Tests the threshold-based rejection logic.
     */
    @Benchmark
    public void submitWithThresholdCheck(Blackhole bh) {
        // Use batcher with threshold to test rejection logic
        var result = batcherSmallQueue.submit("item");
        bh.consume(result);
    }
}
