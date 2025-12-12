package com.vajrapulse.vortex.benchmark;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.BatchResult;
import com.vajrapulse.vortex.results.ItemResult;
import com.vajrapulse.vortex.results.SuccessEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
    private Backend<String> backend;
    
    @Setup(Level.Trial)
    public void setup() {
        backend = batch -> {
            // Simulate minimal processing
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(10))
            .build();
        
        batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry());
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
            ItemResult<String> result = batcher.submit("item-" + index, (item, itemResult) -> {
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
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(MicroBatcherBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}
