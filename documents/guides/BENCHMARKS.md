# Vortex Micro-Batcher Benchmarks

This directory contains JMH (Java Microbenchmark Harness) benchmarks for the Vortex micro-batcher library.

## Running Benchmarks

### Using Gradle

```bash
./gradlew jmh
```

This will compile and run all benchmarks, generating results in `build/reports/jmh/`.

### Using JMH Directly

```bash
./gradlew jmhJar
java -jar build/libs/vortex-jmh.jar
```

## Available Benchmarks

### MicroBatcherBenchmark

Measures throughput of the micro-batcher:
- `submitSingleRequest` - Throughput of single request submission
- `submitConcurrentRequests` - Throughput with 4 concurrent threads
- `submitBatch` - Throughput of batch submission (10 items)

### LatencyBenchmark

Measures latency characteristics:
- `submitAndWaitLatency` - Average time from submission to completion

## Benchmark Configuration

Default configuration:
- **Warmup**: 3 iterations, 1 second each
- **Measurement**: 5 iterations, 1 second each
- **Forks**: 1 (can be increased for more stable results)
- **Mode**: Throughput (ops/sec) for MicroBatcherBenchmark, AverageTime for LatencyBenchmark

## Customizing Benchmarks

Edit the benchmark classes to:
- Adjust batch sizes and linger times
- Test different backend implementations
- Add custom scenarios
- Modify thread counts

## Interpreting Results

### Throughput Benchmarks
Higher is better. Results show operations per second.

### Latency Benchmarks
Lower is better. Results show average time in microseconds.

## Example Output

```
Benchmark                                    Mode  Cnt      Score      Error  Units
MicroBatcherBenchmark.submitSingleRequest   thrpt    5  12345.678  ± 123.45  ops/s
MicroBatcherBenchmark.submitBatch           thrpt    5  5432.109   ± 67.89   ops/s
LatencyBenchmark.submitAndWaitLatency       avgt     5    123.45    ± 12.34   us/op
```

## Tips

1. **Run on dedicated hardware** - Avoid running other applications
2. **Warm up JVM** - Use multiple warmup iterations
3. **Multiple forks** - Increase forks for more stable results
4. **Monitor system** - Check CPU, memory, and GC during runs
5. **Compare configurations** - Test different batch sizes and linger times

