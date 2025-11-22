package com.vajrapulse.vortex.benchmark;

import com.vajrapulse.vortex.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for Vortex MicroBatcher.
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
    
    @Benchmark
    public void submitSingleRequest() {
        CompletableFuture<BatchResult<String>> future = batcher.submit("test-item");
        future.join(); // Wait for completion
    }
    
    @Benchmark
    @Threads(4)
    public void submitConcurrentRequests() {
        // Use thread name instead of deprecated getId()
        String threadName = Thread.currentThread().getName();
        CompletableFuture<BatchResult<String>> future = batcher.submit("test-item-" + threadName);
        future.join();
    }
    
    @Benchmark
    public void submitBatch() {
        List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(batcher.submit("item-" + i));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(MicroBatcherBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}

