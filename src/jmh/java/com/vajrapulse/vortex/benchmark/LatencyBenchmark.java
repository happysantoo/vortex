package com.vajrapulse.vortex.benchmark;

import com.vajrapulse.vortex.MicroBatcher;
import com.vajrapulse.vortex.results.ItemResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Latency benchmarks for Vortex MicroBatcher using the unified submit() API.
 * 
 * Measures:
 * - Submit-to-acceptance latency (immediate return)
 * - Submit-to-processing latency (with callback)
 * - Rejection latency (when queue is full)
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class LatencyBenchmark {
    
    private MicroBatcher<String> batcher;
    private MicroBatcher<String> batcherSmallQueue;
    
    @Setup(Level.Trial)
    public void setup() {
        batcher = BenchmarkBatcherFactory.latencyBatcher();
        
        // Small queue for rejection testing
        batcherSmallQueue = BenchmarkBatcherFactory.smallQueueBatcher(5, 5, 1.0);
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        if (batcher != null) {
            batcher.close();
        }
        if (batcherSmallQueue != null) {
            batcherSmallQueue.close();
        }
    }
    
    /**
     * Measure latency of submit() call (immediate return).
     * This measures the overhead of the submit() method itself.
     */
    @Benchmark
    public long submitLatency(Blackhole bh) {
        long start = System.nanoTime();
        ItemResult<String> result = batcher.submit("test-item");
        long end = System.nanoTime();
        bh.consume(result);
        return end - start;
    }
    
    /**
     * Measure latency from submit to callback invocation (end-to-end).
     * This measures the time from submission until the item is processed.
     */
    @Benchmark
    public long submitToCallbackLatency(Blackhole bh) {
        long[] startTime = new long[1];
        CountDownLatch latch = new CountDownLatch(1);
        
        startTime[0] = System.nanoTime();
        ItemResult<String> result = batcher.submit("test-item", (item, itemResult) -> {
            long endTime = System.nanoTime();
            long latency = endTime - startTime[0];
            bh.consume(itemResult);
            bh.consume(latency);
            latch.countDown();
        });
        bh.consume(result);
        
        // Wait for callback (best effort - may timeout in benchmark)
        try {
            latch.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return 0; // Latency is consumed in callback
    }
    
    /**
     * Measure latency of rejection path (when queue is full).
     */
    @Benchmark
    public long rejectionLatency(Blackhole bh) {
        // Fill queue first
        for (int i = 0; i < 10; i++) {
            batcherSmallQueue.submit("item-" + i);
        }
        
        long start = System.nanoTime();
        ItemResult<String> result = batcherSmallQueue.submit("item-rejected");
        long end = System.nanoTime();
        bh.consume(result);
        return end - start;
    }
    
    /**
     * Measure latency of threshold check (queue rejection threshold).
     */
    @Benchmark
    public long thresholdCheckLatency(Blackhole bh) {
        long start = System.nanoTime();
        ItemResult<String> result = batcherSmallQueue.submit("item");
        long end = System.nanoTime();
        bh.consume(result);
        return end - start;
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(LatencyBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}
