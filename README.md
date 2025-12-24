# Vortex - Micro-Batching Library

[![Maven Central](https://img.shields.io/maven-central/v/com.vajrapulse/vortex?label=Maven%20Central)](https://search.maven.org/artifact/com.vajrapulse/vortex)
[![Java](https://img.shields.io/badge/Java-21+-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)
[![Build Status](https://img.shields.io/github/actions/workflow/status/happysantoo/vortex/build.yml?label=Build)](https://github.com/happysantoo/vortex/actions)
[![Test Coverage](https://img.shields.io/badge/Coverage-82%25-brightgreen.svg)](https://github.com/happysantoo/vortex)

A lightweight Java 21 library for micro-batching requests to any backend. Built with virtual threads, smart batching (size or time-based), comprehensive metrics, and production-ready features.

**Current Version**: 0.0.11

## Features

- ✅ **Java 21** with virtual threads for high concurrency
- ✅ **Smart Batching**: Triggers on batch size OR linger time (whichever comes first)
- ✅ **Unified Submission API**: Single `submit(item, callback)` method with immediate rejection feedback
- ✅ **Type-Safe Results**: Sealed `ItemResult` interface with pattern matching support
- ✅ **Generic Backend**: Works with any backend via the `Backend<T>` interface
- ✅ **Comprehensive Metrics**: Micrometer metrics for queue depth, success/failure rates, latencies, percentiles
- ✅ **Built-in Retry**: Configurable retry support for transient failures
- ✅ **Backpressure Handling**: Automatic queue management with configurable rejection thresholds
- ✅ **Concurrent Dispatch Limiting**: Prevent connection pool exhaustion by limiting concurrent batch dispatches
- ✅ **Graceful Shutdown**: `awaitCompletion()` method for waiting on queue and in-flight batches
- ✅ **Tracing Hooks**: LoggingTracingHook (SLF4J) and MicrometerTracingHook for distributed tracing
- ✅ **Configuration Presets**: High-throughput, low-latency, balanced, and resilient presets
- ✅ **Debug Mode**: Detailed logging for troubleshooting
- ✅ **Auto-Replay**: Automatic replay of successful items when batches have mixed results
- ✅ **Lightweight**: Minimal dependencies, clean code
- ✅ **Simple API**: Easy to use and integrate

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.11</version>
</dependency>
```

### Gradle Dependency

```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.11")
}
```

### Basic Usage

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;
import java.time.Duration;

// 1. Create a backend implementation
Backend<String> backend = batch -> {
    List<SuccessEvent<String>> successes = batch.stream()
        .map(SuccessEvent::new)
        .toList();
    return new BatchResult<>(successes, List.of());
};

// 2. Configure the batcher
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                    // Batch up to 10 items
    .lingerTime(Duration.ofMillis(100)) // Or wait 100ms, whichever comes first
    .maxQueueSize(50)                 // Maximum 50 items in queue
    .build();

// 3. Create and use the batcher
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // Submit with callback for async result handling
    ItemResult<String> result = batcher.submit("item-1", (item, itemResult) -> {
        if (itemResult instanceof ItemResult.Success<String>) {
            System.out.println("Item processed successfully: " + item);
        } else if (itemResult instanceof ItemResult.Failure<String> failure) {
            System.err.println("Item failed: " + failure.error().getMessage());
        }
    });
    
    // Check immediate acceptance/rejection
    if (result instanceof ItemResult.Success<String>) {
        System.out.println("Item accepted!");
    } else if (result instanceof ItemResult.Failure<String> failure) {
        System.err.println("Item rejected: " + failure.error().getMessage());
    }
}
```

## Core API

### Submission API

The library provides two submission methods:

#### Synchronous Submission (`submit`)

Returns `ItemResult` immediately for immediate acceptance/rejection feedback:

```java
// With callback (async result handling)
ItemResult<String> result = batcher.submit("item", itemResult -> {
    // Callback fires when item is processed (typically 10-50ms after submission)
    if (itemResult instanceof ItemResult.Success<String> success) {
        System.out.println("Success: " + success.getItem());
    } else if (itemResult instanceof ItemResult.Failure<String> failure) {
        System.err.println("Failed: " + failure.error().getMessage());
    }
});

// Without callback (fire and forget)
ItemResult<String> result = batcher.submit("item", null);

// Check immediate rejection
if (result instanceof ItemResult.Failure<String> failure) {
    // Item was rejected immediately (queue full, etc.)
    handleRejection(failure.error());
}
```

**Key Points:**
- Returns `ItemResult` immediately (Success = accepted, Failure = rejected)
- Callback (if provided) fires asynchronously when item is processed
- Callback receives individual item's result, not the full batch result
- Thread-safe and can be called from multiple threads

#### Asynchronous Submission (`submitAsync`)

Returns `CompletableFuture<ItemResult<T>>` for async/await-style programming:

```java
// Chain operations with CompletableFuture
CompletableFuture<ItemResult<String>> future = batcher.submitAsync("item");
future
    .thenApply(result -> {
        if (result instanceof ItemResult.Success<String>) {
            return processSuccess(result.getItem());
        } else if (result instanceof ItemResult.Failure<String> failure) {
            return handleFailure(failure.error());
        }
        return null;
    })
    .thenAccept(processed -> System.out.println("Processed: " + processed))
    .exceptionally(throwable -> {
        if (throwable instanceof ItemRejectedException) {
            // Handle immediate rejection
            handleRejection(throwable);
        }
        return null;
    });

// Or use with CompletableFuture.allOf() for batch operations
List<CompletableFuture<ItemResult<String>>> futures = items.stream()
    .map(batcher::submitAsync)
    .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenRun(() -> System.out.println("All items processed"));
```

**Key Points:**
- Returns `CompletableFuture<ItemResult<T>>` immediately (never blocks)
- Completes exceptionally with `ItemRejectedException` if queue is full
- Completes with `ItemResult` when batch processing finishes
- Perfect for chaining operations and composing async workflows

### ItemResult Types

`ItemResult<T>` is a sealed interface with two variants:

```java
// Success - item was accepted or processed successfully
ItemResult.Success<T> success = ItemResult.success(item);

// Failure - item was rejected or processing failed
ItemResult.Failure<T> failure = ItemResult.failure(item, error);
```

**Pattern Matching (Java 21+):**

```java
ItemResult<String> result = batcher.submit("item", null);

switch (result) {
    case ItemResult.Success<String> success -> 
        System.out.println("Success: " + success.item());
    case ItemResult.Failure<String> failure -> 
        System.err.println("Failed: " + failure.error().getMessage());
}
```

## Configuration

### Basic Configuration

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                           // Items per batch
    .lingerTime(Duration.ofMillis(100))      // Max wait time
    .maxQueueSize(50)                        // Queue capacity
    .build();
```

### Configuration Presets

Use presets for common scenarios:

```java
// High Throughput (large batches, longer wait)
BatcherConfig highThroughput = BatcherConfig.highThroughputPreset();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, highThroughput, registry);

// Low Latency (small batches, short wait)
BatcherConfig lowLatency = BatcherConfig.lowLatencyPreset();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, lowLatency, registry);

// Balanced (default)
BatcherConfig balanced = BatcherConfig.balancedPreset();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, balanced, registry);

// Resilient (with retry)
Predicate<Throwable> retryable = e -> e instanceof IOException;
BatcherConfig resilient = BatcherConfig.resilientPreset(retryable);
MicroBatcher<String> batcher = new MicroBatcher<>(backend, resilient, registry);
```

### Advanced Configuration

```java
BatcherConfig config = BatcherConfig.builder()
    // Batching
    .batchSize(20)                           // Batch up to 20 items
    .lingerTime(Duration.ofMillis(200))      // Or wait 200ms
    
    // Queue Management
    .maxQueueSize(100)                       // Max 100 items in queue
    .queueRejectionThreshold(0.9)            // Reject at 90% capacity
    
    // Concurrent Limiting
    .maxConcurrentBatches(5)                 // Max 5 concurrent batches
    
    // Retry
    .maxRetries(3)                           // Retry failed items up to 3 times
    .retryDelay(Duration.ofMillis(100))      // Wait 100ms between retries
    .retryableErrorPredicate(e ->            // Only retry transient errors
        e instanceof IOException || 
        e instanceof TimeoutException)
    
    // Atomic Commit
    .atomicCommit(true)                      // Batch fails if any item fails
    
    // Auto-Replay
    .autoReplaySuccesses(true)               // Replay successful items in mixed batches
    
    // Metrics
    .perItemMetrics(true)                    // Track per-item metrics
    
    // Debugging
    .debugMode(true)                         // Enable debug logging
    
    // Tracing
    .tracingHook(new LoggingTracingHook())  // Add tracing hook
    
    .build();
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `batchSize` | `int` | 10 | Maximum number of requests per batch |
| `lingerTime` | `Duration` | 100ms | Maximum time to wait before dispatching a batch |
| `maxQueueSize` | `int` | `2 × batchSize` | Maximum queue size for pending requests |
| `queueRejectionThreshold` | `double` | 1.0 | Percentage (0.0-1.0) at which to start rejecting items |
| `maxConcurrentBatches` | `int` | 0 (unlimited) | Maximum concurrent batch dispatches |
| `atomicCommit` | `boolean` | false | If true, entire batch fails if any request fails |
| `autoReplaySuccesses` | `boolean` | false | Automatically replay successful items when batch has mixed results |
| `perItemMetrics` | `boolean` | false | Enable detailed per-item metrics (adds overhead) |
| `debugMode` | `boolean` | false | Enable detailed debug logging |
| `maxRetries` | `int` | 0 | Maximum number of retries for failed items |
| `retryDelay` | `Duration` | 0ms | Delay between retry attempts |
| `retryableErrorPredicate` | `Predicate<Throwable>` | `e -> false` | Determines which errors should be retried |
| `tracingHook` | `BatchTracingHook` | null | Optional tracing hook for observability |

## Exception Handling

### ItemRejectedException

Thrown when an item is rejected due to capacity constraints:

```java
ItemResult<String> result = batcher.submit("item", null);

if (result instanceof ItemResult.Failure<String> failure) {
    Throwable error = failure.error();
    
    if (error instanceof ItemRejectedException rejected) {
        // Item was rejected
        System.out.println("Rejection source: " + rejected.getSourceName());
        System.out.println("Current level: " + rejected.getCurrentLevel());
        System.out.println("Max level: " + rejected.getMaxLevel());
        
        // Handle rejection
        handleRejection(rejected);
    }
}
```

**Rejection Sources:**
- **"Vortex Queue Depth"**: Queue is full or reached rejection threshold
- **"Concurrent Batches"**: Too many batches are being dispatched concurrently

### Other Exceptions

- **`IllegalStateException`**: Thrown when batcher is closed
- **`NullPointerException`**: Thrown when submitting a null item

**For detailed exception handling, see [User Guide](documents/guides/USER_GUIDE.md#exception-handling)**

## Backpressure and Queue Management

### Understanding Backpressure

Backpressure occurs when the system cannot keep up with incoming requests. MicroBatcher provides built-in backpressure handling through queue management.

### Queue Configuration

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                    // Items per batch
    .maxQueueSize(50)                 // Maximum items in queue
    .queueRejectionThreshold(0.8)     // Reject when queue is 80% full (0.0 to 1.0)
    .build();
```

**Key Settings:**
- **`maxQueueSize`**: Maximum number of items that can be queued (default: `2 * batchSize`)
- **`queueRejectionThreshold`**: Percentage (0.0 to 1.0) at which to start rejecting (default: 1.0)

### Handling Rejections

```java
ItemResult<String> result = batcher.submit("item", null);

if (result instanceof ItemResult.Failure<String> failure) {
    if (failure.error() instanceof ItemRejectedException rejected) {
        // Handle rejection: retry, overflow queue, fail fast, etc.
        handleRejection(item, rejected);
    }
}
```

**For detailed backpressure strategies, see [User Guide](documents/guides/USER_GUIDE.md#backpressure-and-queue-management)**

## Advanced Features

### Retry Support

```java
BatcherConfig config = BatcherConfig.builder()
    .maxRetries(3)
    .retryDelay(Duration.ofMillis(100))
    .retryableErrorPredicate(e -> 
        e instanceof IOException || 
        e instanceof TimeoutException)
    .build();

// Failed items are automatically retried
batcher.submit("item", (item, result) -> {
    // This callback may be called multiple times if retries occur
    if (result instanceof ItemResult.Success<String>) {
        System.out.println("Item succeeded (possibly after retry)");
    }
});
```

### Atomic Commit Mode

In atomic commit mode, if any item in a batch fails, the entire batch is considered failed:

```java
BatcherConfig config = BatcherConfig.builder()
    .atomicCommit(true)  // Enable atomic commit
    .build();

// If any item fails, all items in the batch fail
```

### Graceful Shutdown

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // Submit items
    batcher.submit("item-1", null);
    batcher.submit("item-2", null);
    
    // Wait for all items to be processed
    boolean completed = batcher.awaitCompletion(5, TimeUnit.SECONDS);
    
    if (completed) {
        System.out.println("All items processed");
    } else {
        System.out.println("Timeout - some items may still be processing");
    }
    
    // close() is called automatically by try-with-resources
}
```

### Metrics and Monitoring

```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Rate metrics
double successRate = metrics.getSuccessRate();
double failureRate = metrics.getFailureRate();
double rejectionRate = metrics.getRejectionRate();

// Latency metrics
double avgLatency = metrics.getAverageLatency();
double p95Latency = metrics.getPercentileLatency(0.95);
double p99Latency = metrics.getPercentileLatency(0.99);

// Queue metrics
int queueDepth = batcher.getQueueDepth();
```

**For complete metrics documentation, see [User Guide](documents/guides/USER_GUIDE.md#advanced-features)**

## Documentation

- **[Complete User Guide](documents/guides/USER_GUIDE.md)**: Comprehensive guide covering all features, exception handling, backpressure, sync/async usage, and best practices
- **[Examples](examples/)**: Working code examples for common scenarios
- **[Benchmarks](documents/guides/BENCHMARKS.md)**: Performance benchmarks and results

## Examples

See the `examples/` directory for comprehensive examples. **Examples are automatically compiled as part of the build** to ensure they stay current with the API.

### Key Examples

- **ThreeSubmissionMethodsExample.java** - Demonstrates all three submission methods (`submit()`, `submit(item, callback)`, `submitAsync()`)
- **ErrorHandlingExample.java** - Comprehensive error handling (queue full, backend errors, retries)
- **BasicUsageExample.java** - Simple getting started example
- **MetricsExample.java** - Metrics collection and monitoring
- **TracingExample.java** - Tracing integration for observability

### Running Examples

Examples are compiled automatically during build:

```bash
# Compile examples (runs automatically during build)
./gradlew compileExamplesJava

# Run an example manually (after compilation)
java -cp "build/classes/java/main:build/classes/examples:$(./gradlew -q printClasspath)" \
  com.vajrapulse.vortex.example.BasicUsageExample
```

For more examples and details, see [examples/README.md](examples/README.md).

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
- **HTML Report**: `build/reports/jmh/html/index.html`
- **JSON Results**: `build/reports/jmh/results.json`
- **Text Results**: `build/results/jmh/results.txt`

For more details, see [BENCHMARKS.md](documents/guides/BENCHMARKS.md).

## Requirements

- **Java 21+** (for virtual threads)
- **Gradle 9.2.0+** (for building)

## Building

```bash
./gradlew build
```

## Best Practices

### 1. Always Handle Rejections

```java
// ✅ Good - handles rejections
ItemResult<String> result = batcher.submit("item", null);
if (result instanceof ItemResult.Failure<String> failure) {
    handleRejection(failure.error());
}

// ❌ Bad - ignores rejections
batcher.submit("item", null);
```

### 2. Use Try-With-Resources

```java
// ✅ Good - automatic cleanup
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // Use batcher
}

// ❌ Bad - manual cleanup (easy to forget)
MicroBatcher<String> batcher = new MicroBatcher<>(backend, config);
// ... use batcher ...
batcher.close(); // Easy to forget!
```

### 3. Handle Callback Exceptions

```java
// ✅ Good - callback exceptions don't crash the system
batcher.submit("item", (item, result) -> {
    try {
        processResult(result);
    } catch (Exception e) {
        logger.error("Error processing result", e);
    }
});
```

### 4. Use Configuration Presets

```java
// ✅ Good - use presets
BatcherConfig config = BatcherConfig.highThroughputPreset();

// ❌ Bad - manual configuration (may not be optimal)
BatcherConfig config = BatcherConfig.builder()
    .batchSize(100)
    .lingerTime(Duration.ofMillis(500))
    .maxQueueSize(500)
    .build();
```

**For more best practices, see [User Guide](documents/guides/USER_GUIDE.md#best-practices)**

## Migration from 0.0.9

### Breaking Changes

- **Removed Factory Methods**: `forHighThroughput()`, `forLowLatency()`, `forBalanced()`, `forResilient()`
  - **Migration**: Use constructors with `BatcherConfig` presets:
    ```java
    // Old
    MicroBatcher<String> batcher = MicroBatcher.forHighThroughput(backend, registry);
    
    // New
    MicroBatcher<String> batcher = new MicroBatcher<>(
        backend, 
        BatcherConfig.highThroughputPreset(), 
        registry
    );
    ```

- **Removed Dynamic Configuration**: `updateBatchSize()`, `updateLingerTime()`, `getCurrentBatchSize()`, `getCurrentLingerTime()`
  - **Migration**: Configuration is now immutable. Create a new `BatcherConfig` if you need different settings.

### New Features

- **Simplified API**: Removed redundant factory methods and dynamic configuration
- **Configuration Presets**: Added preset factory methods to `BatcherConfig` for common scenarios
- **Improved Coverage**: Increased test coverage to 82% for `MicroBatcher` class

### Recommended Updates

1. Replace factory methods with constructors using presets
2. Remove any calls to `updateBatchSize()` or `updateLingerTime()`
3. Use `BatcherConfig` presets for common scenarios
4. Review [User Guide](documents/guides/USER_GUIDE.md) for detailed usage patterns

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please see our contributing guidelines for more information.

## Support

For questions, issues, or feature requests, please open an issue on [GitHub](https://github.com/happysantoo/vortex).
