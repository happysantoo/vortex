package com.vajrapulse.vortex.benchmark;

import com.vajrapulse.vortex.MicroBatcher;
import com.vajrapulse.vortex.results.ItemResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Throughput benchmarks for Vortex MicroBatcher using the unified submit() API.
 * 
 * Measures:
 * - Single request throughput
 * - Concurrent request throughput
 * - Batch processing throughput
 * 
 * Run with: ./gradlew jmh
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MicroBatcherBenchmark {
    
    private MicroBatcher<String> batcher;
    
    @Setup(Level.Trial)
    public void setup() {
        batcher = BenchmarkBatcherFactory.defaultBatcher();
    }
    
    @TearDown(Level.Trial)
    public void tearDown() {
        if (batcher != null) {
            batcher.close();
        }
    }
    
    /**
     * Benchmark single request submission throughput.
     */
    @Benchmark
    public void submitSingleRequest(Blackhole bh) {
        ItemResult<String> result = batcher.submit("test-item");
        bh.consume(result);
    }
    
    /**
     * Benchmark concurrent request submission throughput.
     */
    @Benchmark
    @Threads(4)
    public void submitConcurrentRequests(Blackhole bh) {
        String threadName = Thread.currentThread().getName();
        ItemResult<String> result = batcher.submit("test-item-" + threadName);
        bh.consume(result);
    }
    
    /**
     * Benchmark batch submission throughput with callback.
     */
    @Benchmark
    public void submitBatchWithCallback(Blackhole bh) {
        CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            final int index = i;
            ItemResult<String> result = batcher.submit("item-" + index, itemResult -> {
                bh.consume(itemResult);
                latch.countDown();
            });
            bh.consume(result);
        }
        // Wait for all callbacks to complete (best effort - may timeout in benchmark)
        try {
            latch.await(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Benchmark batch submission throughput without callback.
     */
    @Benchmark
    public void submitBatchWithoutCallback(Blackhole bh) {
        for (int i = 0; i < 10; i++) {
            ItemResult<String> result = batcher.submit("item-" + i);
            bh.consume(result);
        }
    }
    
    /**
     * Benchmark submitAsync() throughput - single request.
     */
    @Benchmark
    public void submitAsyncSingleRequest(Blackhole bh) {
        CompletableFuture<ItemResult<String>> future = batcher.submitAsync("test-item");
        bh.consume(future);
    }
    
    /**
     * Benchmark submitAsync() throughput - concurrent requests.
     */
    @Benchmark
    @Threads(4)
    public void submitAsyncConcurrentRequests(Blackhole bh) {
        String threadName = Thread.currentThread().getName();
        CompletableFuture<ItemResult<String>> future = batcher.submitAsync("test-item-" + threadName);
        bh.consume(future);
    }
    
    /**
     * Benchmark submitAsync() throughput - batch with completion waiting.
     */
    @Benchmark
    @SuppressWarnings("unchecked")
    public void submitAsyncBatchWithCompletion(Blackhole bh) {
        CompletableFuture<ItemResult<String>>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = batcher.submitAsync("item-" + i);
        }
        // Wait for all to complete (best effort - may timeout in benchmark)
        try {
            CompletableFuture.allOf(futures).get(100, TimeUnit.MILLISECONDS);
            for (CompletableFuture<ItemResult<String>> future : futures) {
                bh.consume(future.join());
            }
        } catch (Exception e) {
            // Best effort - consume what we have
            for (CompletableFuture<ItemResult<String>> future : futures) {
                if (future.isDone()) {
                    bh.consume(future.join());
                }
            }
        }
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(MicroBatcherBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}
