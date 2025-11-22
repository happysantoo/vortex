# Vortex - Micro-Batching Library

A lightweight Java 21 library for micro-batching requests to any backend. Built with virtual threads, smart batching (size or time-based), and comprehensive Micrometer metrics.

## Features

- ✅ **Java 21** with virtual threads for high concurrency
- ✅ **Smart Batching**: Triggers on batch size OR linger time (whichever comes first)
- ✅ **Atomic Commits**: Optional atomic commit mode where batch fails if any request fails
- ✅ **Generic Backend**: Works with any backend via the `Backend<T>` interface
- ✅ **Comprehensive Metrics**: Micrometer metrics for queue depth, success/failure rates, latencies
- ✅ **Lightweight**: Minimal dependencies, clean code
- ✅ **Simple API**: Easy to use and integrate

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.1</version>
</dependency>
```

### Gradle Dependency

```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.1")
}
```

### Basic Usage

```java
// 1. Create a backend implementation
// Backend can be blocking - it will run on a virtual thread
Backend<String> backend = batch -> {
    // Your backend logic here (can be blocking I/O)
    // Since this runs on a virtual thread, blocking is efficient
    List<SuccessEvent<String>> successes = new ArrayList<>();
    List<FailureEvent<String>> failures = new ArrayList<>();
    
    for (String item : batch) {
        // Process item (e.g., HTTP call, database query)
        // Can throw Exception if processing fails
        successes.add(new SuccessEvent<>(item));
    }
    
    return new BatchResult<>(successes, failures);
    // Note: Can throw Exception - will be handled by the batcher
};

// 2. Configure the batcher
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                    // Batch size trigger
    .lingerTime(Duration.ofMillis(100)) // Time-based trigger
    .atomicCommit(false)               // Optional: all-or-nothing
    .maxConcurrency(10)                // Max concurrent batches
    .build();

// 3. Create and use the batcher
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // submit() returns CompletableFuture for async client handling
    CompletableFuture<BatchResult<String>> future = batcher.submit("request-data");
    
    // Wait for result (or use async callbacks)
    BatchResult<String> result = future.get();
    // Handle result...
    
    // Or use async callbacks:
    // future.thenAccept(result -> { /* handle result */ });
}
```

## Configuration

### BatcherConfig

- `batchSize(int)`: Maximum number of requests per batch (default: 10)
- `lingerTime(Duration)`: Maximum time to wait before dispatching a batch (default: 100ms)
- `atomicCommit(boolean)`: If true, entire batch fails if any request fails (default: false)
- `maxConcurrency(int)`: Maximum concurrent batch dispatches (default: 10) - Note: With virtual threads, this is less of a concern
- `autoReplaySuccesses(boolean)`: If true, successful items are automatically replayed once before returning failures (default: false)

### Smart Batching

Batches are dispatched when **either**:
- The batch size is reached, OR
- The linger time has elapsed

This ensures optimal throughput and low latency.

## Metrics

The library exposes the following Micrometer metrics:

- `vortex.requests.submitted` - Total requests submitted
- `vortex.batches.dispatched` - Total batches dispatched
- `vortex.requests.succeeded` - Total successful requests
- `vortex.requests.failed` - Total failed requests
- `vortex.requests.replayed` - Total successful requests that were replayed (when autoReplaySuccesses is enabled)
- `vortex.queue.depth` - Current queue depth (gauge)
- `vortex.batch.dispatch.latency` - Time to dispatch a batch
- `vortex.request.wait.latency` - Time a request waits before batching

Access metrics via `batcher.getMeterRegistry()`.

## Atomic Commits

When `atomicCommit` is enabled:
- If any request in a batch fails, the entire batch is marked as failed
- All requests in the batch receive a failure event
- Useful for transactional operations where partial success is not acceptable

## Auto-Replay Successes

The library supports automatic replay of successful items when a batch contains both successes and failures. This is useful for scenarios like:
- **Atomic backends** (e.g., database inserts with unique constraints) where some items are rejected and need reprocessing
- **Backends that handle success/failures internally** and don't need replay

### Configuration

Replay can be controlled in two ways:

1. **Backend Decision** (Recommended): Implement `shouldReplaySuccesses()` in your Backend
   ```java
   Backend<String> backend = new Backend<String>() {
       @Override
       public BatchResult<String> dispatch(List<String> batch) throws Exception {
           // Your dispatch logic
           return new BatchResult<>(successes, failures);
       }
       
       @Override
       public boolean shouldReplaySuccesses(BatchResult<String> result) {
           // Atomic backend: replay when there are failures
           return !result.getFailures().isEmpty() && !result.getSuccesses().isEmpty();
       }
   };
   ```

2. **Config Fallback**: Use `autoReplaySuccesses(true)` in BatcherConfig
   - Only used if backend doesn't override `shouldReplaySuccesses()`
   - Default: `false`

### Behavior

- Only replays when there are **both** successes and failures in the same batch
- Successful items are re-submitted to the batcher for another attempt
- Failures are returned to clients immediately
- Backend decision takes precedence over config

## Architecture

- **Virtual Threads**: Uses Java 21 virtual threads for efficient I/O-bound operations
- **Synchronous Backend API**: Backend interface is simple and synchronous - **you don't need to return `CompletableFuture`**. Just return `BatchResult` directly. Can throw `Exception` for error handling.
- **Automatic Thread Management**: Backend dispatch runs on virtual threads automatically - blocking I/O is efficient
- **Non-blocking Client API**: The batcher's `submit()` method returns `CompletableFuture<BatchResult<T>>` to clients for async result handling, but your backend implementation stays simple and synchronous
- **Thread-safe**: Safe for concurrent use from multiple threads
- **Resource Management**: Implements `AutoCloseable` for proper cleanup

## Requirements

- Java 21+
- Gradle 9.2.0+

## Building

```bash
./gradlew build
```

## Benchmarks

The project includes JMH (Java Microbenchmark Harness) benchmarks to measure performance characteristics.

### Running Benchmarks

```bash
# Run benchmarks and generate HTML report
./gradlew jmh jmhReport

# Or just run benchmarks
./gradlew jmh
```

### Benchmark Results

After running benchmarks, view the HTML report:
- **HTML Report**: [build/reports/jmh/html/index.html](build/reports/jmh/html/index.html)
- **JSON Results**: [build/reports/jmh/results.json](build/reports/jmh/results.json)
- **Text Results**: [build/results/jmh/results.txt](build/results/jmh/results.txt)

The benchmarks measure:
- **Throughput**: Operations per second for single requests, concurrent requests, and batch submissions
- **Latency**: Average time from submission to completion

For more details, see [BENCHMARKS.md](documents/guides/BENCHMARKS.md).

## Design

For a detailed explanation of the architecture, request flow, and design decisions, see [DESIGN.md](documents/architecture/DESIGN.md).

## Example

See `src/main/java/com/vortex/batcher/example/ExampleUsage.java` for a complete example.

## Design Principles

1. **Simplicity**: Minimal API surface, easy to understand
2. **Performance**: Virtual threads for high concurrency
3. **Observability**: Comprehensive metrics out of the box
4. **Flexibility**: Generic backend interface works with any system
5. **Lightweight**: Minimal dependencies

## Improvements & Future Enhancements

Potential improvements you might consider:

1. **Backpressure**: Add configurable backpressure strategies when queue is full
2. **Retry Logic**: Built-in retry mechanisms for failed batches
3. **Circuit Breaker**: Circuit breaker pattern for backend failures
4. **Custom Result Mapping**: More sophisticated result-to-request mapping
5. **Priority Queues**: Support for priority-based batching
6. **Batch Size Hints**: Dynamic batch sizing based on load
7. **Distributed Tracing**: Integration with tracing frameworks
8. **Health Checks**: Health check endpoints for monitoring

## License

MIT

