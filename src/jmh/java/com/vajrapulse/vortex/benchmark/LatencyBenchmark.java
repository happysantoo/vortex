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
 * Latency benchmarks for Vortex MicroBatcher.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class LatencyBenchmark {
    
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
    public long submitAndWaitLatency() {
        long start = System.nanoTime();
        CompletableFuture<BatchResult<String>> future = batcher.submit("test-item");
        future.join();
        return System.nanoTime() - start;
    }
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(LatencyBenchmark.class.getSimpleName())
            .build();
        
        new Runner(opt).run();
    }
}

