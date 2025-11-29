# Vortex - Micro-Batching Library

A lightweight Java 21 library for micro-batching requests to any backend. Built with virtual threads, smart batching (size or time-based), comprehensive metrics, and production-ready features.

**Version**: 0.0.3

## Features

- ✅ **Java 21** with virtual threads for high concurrency
- ✅ **Smart Batching**: Triggers on batch size OR linger time (whichever comes first)
- ✅ **Atomic Commits**: Optional atomic commit mode where batch fails if any request fails
- ✅ **Generic Backend**: Works with any backend via the `Backend<T>` interface
- ✅ **Comprehensive Metrics**: Micrometer metrics for queue depth, success/failure rates, latencies, percentiles
- ✅ **Built-in Retry**: Configurable retry support for transient failures
- ✅ **Item Result Tracking**: Type-safe sealed `ItemResult` interface with pattern matching
- ✅ **Batch Callbacks**: Submit items with callbacks for async result handling
- ✅ **Per-Item Metrics**: Optional detailed metrics for individual items
- ✅ **Dynamic Configuration**: Update batch size and linger time at runtime
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
    <version>0.0.3</version>
</dependency>
```

### Gradle Dependency

```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.3")
}
```

### Basic Usage

```java
import com.vajrapulse.vortex.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// 1. Create a backend implementation
// Backend can be blocking - it will run on a virtual thread
Backend<String> backend = batch -> {
    List<SuccessEvent<String>> successes = new ArrayList<>();
    List<FailureEvent<String>> failures = new ArrayList<>();
    
    for (String item : batch) {
        try {
            // Process item (e.g., HTTP call, database query)
            // Can throw Exception if processing fails
            processItem(item);
            successes.add(new SuccessEvent<>(item));
        } catch (Exception e) {
            failures.add(new FailureEvent<>(item, e));
        }
    }
    
    return new BatchResult<>(successes, failures);
    // Note: Can throw Exception - will be handled by the batcher
};

// 2. Configure the batcher
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                    // Batch size trigger
    .lingerTime(Duration.ofMillis(100)) // Time-based trigger
    .atomicCommit(false)               // Optional: all-or-nothing
    .build();

// 3. Create and use the batcher
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // submit() returns CompletableFuture for async client handling
    CompletableFuture<BatchResult<String>> future = batcher.submit("request-data");
    
    // Wait for result (or use async callbacks)
    BatchResult<String> result = future.get();
    
    // Check if all succeeded
    if (result.isAllSuccess()) {
        // Handle success...
    }
    
    // Or use async callbacks:
    // future.thenAccept(result -> { /* handle result */ });
}
```

## Configuration

### BatcherConfig Builder

The `BatcherConfig` uses a builder pattern for configuration:

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                          // Max items per batch (default: 10)
    .lingerTime(Duration.ofMillis(100))     // Max wait time (default: 100ms)
    .atomicCommit(false)                     // All-or-nothing mode (default: false)
    .autoReplaySuccesses(false)              // Auto-replay successful items (default: false)
    .perItemMetrics(false)                   // Enable per-item metrics (default: false)
    .debugMode(false)                        // Enable debug logging (default: false)
    .maxRetries(0)                           // Max retries for failures (default: 0)
    .retryDelay(Duration.ZERO)               // Delay between retries (default: 0)
    .retryableErrorPredicate(e -> false)     // Which errors to retry (default: none)
    .maxQueueSize(20)                        // Max queue size (default: 2 × batchSize)
    .build();
```

### Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `batchSize` | `int` | 10 | Maximum number of requests per batch |
| `lingerTime` | `Duration` | 100ms | Maximum time to wait before dispatching a batch |
| `atomicCommit` | `boolean` | false | If true, entire batch fails if any request fails |
| `autoReplaySuccesses` | `boolean` | false | Automatically replay successful items when batch has mixed results |
| `perItemMetrics` | `boolean` | false | Enable detailed per-item metrics (adds overhead) |
| `debugMode` | `boolean` | false | Enable detailed debug logging |
| `maxRetries` | `int` | 0 | Maximum number of retries for failed items |
| `retryDelay` | `Duration` | 0ms | Delay between retry attempts |
| `retryableErrorPredicate` | `Predicate<Throwable>` | `e -> false` | Determines which errors should be retried |
| `maxQueueSize` | `int` | `2 × batchSize` | Maximum queue size for pending requests (backpressure control) |

### Smart Batching

Batches are dispatched when **either**:
- The batch size is reached, OR
- The linger time has elapsed

This ensures optimal throughput and low latency. Whichever condition is met first triggers the batch dispatch.

## Core Concepts

### ItemResult - Type-Safe Results

The library provides a sealed `ItemResult<T>` interface for type-safe result handling:

```java
// Find a specific item's result
Optional<ItemResult<String>> itemResult = batchResult.findItemResult("my-item");

if (itemResult.isPresent()) {
    ItemResult<String> result = itemResult.get();
    
    // Pattern matching with sealed interface
    switch (result) {
        case ItemResult.Success<String> success -> {
            System.out.println("Item succeeded: " + success.getItem());
        }
        case ItemResult.Failure<String> failure -> {
            System.out.println("Item failed: " + failure.getError().getMessage());
        }
    }
}
```

### BatchResult - Enhanced Error Handling

`BatchResult` provides several convenience methods:

```java
BatchResult<String> result = future.get();

// Check batch status
if (result.isAllSuccess()) { /* all succeeded */ }
if (result.isCompleteFailure()) { /* all failed */ }
if (result.isCompleteSuccess()) { /* alias for isAllSuccess() */ }

// Get failure rate (0.0 to 1.0)
double failureRate = result.getFailureRate();

// Group failures by error type
Map<Class<? extends Throwable>, List<FailureEvent<String>>> failuresByType = 
    result.getFailuresByType();

// Find specific item result
Optional<ItemResult<String>> itemResult = result.findItemResult("item-1");
```

### Submitting Items

#### Basic Submission

```java
CompletableFuture<BatchResult<String>> future = batcher.submit("item");
BatchResult<String> result = future.get();
```

#### Submission with Callback

```java
CompletableFuture<Void> callbackFuture = batcher.submitWithCallback(
    "item",
    (item, result) -> {
        if (result instanceof ItemResult.Success<String> success) {
            System.out.println("Success: " + success.getItem());
        } else if (result instanceof ItemResult.Failure<String> failure) {
            System.out.println("Failed: " + failure.getError().getMessage());
        }
    }
);

// Always handle exceptions to detect backpressure
callbackFuture.exceptionally(throwable -> {
    if (throwable.getCause() instanceof RejectedExecutionException) {
        System.err.println("Request rejected: Queue is full (backpressure)");
        // Handle backpressure: retry, log, or send to dead letter queue
    }
    return null;
});
```

## Advanced Features

### Retry Support

Configure automatic retry for transient failures:

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .maxRetries(3)                                    // Retry up to 3 times
    .retryDelay(Duration.ofMillis(100))               // Wait 100ms between retries
    .retryableErrorPredicate(e -> 
        e instanceof IOException ||                   // Retry I/O errors
        e instanceof TimeoutException                // Retry timeouts
    )
    .build();
```

**How it works:**
- Failed items matching the `retryableErrorPredicate` are automatically retried
- Retries respect `maxRetries` limit
- `retryDelay` is applied between retry attempts
- Original `CompletableFuture` completes when retry succeeds or max retries reached

### Dynamic Configuration

Update batch size and linger time at runtime:

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    // Update batch size (applies to next batch)
    batcher.updateBatchSize(20);
    
    // Update linger time (applies to next batch)
    batcher.updateLingerTime(Duration.ofMillis(200));
    
    // Get current values
    int currentBatchSize = batcher.getCurrentBatchSize();
    Duration currentLingerTime = batcher.getCurrentLingerTime();
}
```

**Note**: Updates apply to *next* batch being formed, not the current batch. Updates are thread-safe.

### Per-Item Metrics

Enable detailed metrics for individual items:

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .perItemMetrics(true)  // Enable per-item metrics
    .build();
```

When enabled, the following metrics are recorded per item:
- `vortex.item.submit.latency` - Time from submit to batch completion
- `vortex.item.wait.time` - Time item waits in queue before batching
- `vortex.item.batch.size` - Size of batch when item was processed

**Note**: Per-item metrics add overhead. Only enable when needed for detailed analysis.

### Debug Mode

Enable detailed logging for troubleshooting:

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .debugMode(true)  // Enable debug logging
    .build();
```

Debug mode logs:
- Batch formation events
- Item submission events
- Batch dispatch events
- Queue depth changes
- Timing information

## Metrics

The library exposes comprehensive Micrometer metrics and provides a convenient `MetricsProvider` interface for easy access.

### Using MetricsProvider (Recommended)

The `MetricsProvider` interface provides convenient, domain-specific access to key metrics:

```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Get failure rate (0.0 to 1.0)
double failureRate = metrics.getFailureRate();

// Get success rate (0.0 to 1.0)
double successRate = metrics.getSuccessRate();

// Get queue depth
int queueDepth = metrics.getQueueDepth();

// Get totals
long submitted = metrics.getTotalSubmitted();
long succeeded = metrics.getTotalSucceeded();
long failed = metrics.getTotalFailed();

// Get latency metrics
double avgLatency = metrics.getAverageDispatchLatency();
double p95Latency = metrics.getP95DispatchLatency();
double p99Latency = metrics.getP99DispatchLatency();
```

**Use Cases:**
- **Adaptive Batch Sizing**: Adjust batch size based on failure rate
- **Circuit Breaker**: Open circuit when failure rate exceeds threshold
- **Auto-Scaling**: Scale backend workers based on queue depth
- **Health Monitoring**: Check system health using success/failure rates

**Example - Adaptive Batching:**
```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Adjust batch size based on failure rate
if (metrics.getFailureRate() > 0.1) {
    batcher.updateBatchSize(5); // Reduce batch size
} else if (metrics.getFailureRate() < 0.01) {
    batcher.updateBatchSize(20); // Increase batch size
}
```

### Direct MeterRegistry Access

For advanced use cases, you can access the underlying Micrometer registry:

```java
MeterRegistry registry = batcher.getMeterRegistry();
double queueDepth = registry.gauge("vortex.queue.depth", 0.0);
long submitted = registry.counter("vortex.requests.submitted").count();
```

### Core Metrics

- `vortex.requests.submitted` - Total requests submitted (Counter)
- `vortex.batches.dispatched` - Total batches dispatched (Counter)
- `vortex.requests.succeeded` - Total successful requests (Counter)
- `vortex.requests.failed` - Total failed requests (Counter)
- `vortex.requests.replayed` - Total successful requests that were replayed (Counter)
- `vortex.queue.depth` - Current queue depth (Gauge)
- `vortex.batch.dispatch.latency` - Time to dispatch a batch (Timer)
- `vortex.request.wait.latency` - Time a request waits before being batched (Timer)
- `vortex.queue.wait.time` - Distribution of queue wait times with percentiles (Timer)
  - Includes p50, p95, p99 percentiles
- `vortex.batch.size` - Distribution of batch sizes (DistributionSummary)

### Per-Item Metrics (when enabled)

- `vortex.item.submit.latency` - Time from submit to batch completion (Timer)
- `vortex.item.wait.time` - Time item waits in queue (Timer)
- `vortex.item.batch.size` - Size of batch when item was processed (DistributionSummary)

### Accessing Metrics

```java
MeterRegistry registry = batcher.getMeterRegistry();

// Get counters
long submitted = registry.counter("vortex.requests.submitted").count();
long succeeded = registry.counter("vortex.requests.succeeded").count();

// Get timers
double avgLatency = registry.timer("vortex.batch.dispatch.latency")
    .mean(TimeUnit.MILLISECONDS);

// Get percentiles
double p95 = registry.timer("vortex.queue.wait.time")
    .percentile(0.95, TimeUnit.MILLISECONDS);

// Get gauges
double queueDepth = registry.gauge("vortex.queue.depth", 0.0);
```

## Atomic Commits

When `atomicCommit` is enabled:
- If any request in a batch fails, the entire batch is marked as failed
- All requests in the batch receive a failure event
- Useful for transactional operations where partial success is not acceptable

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .atomicCommit(true)  // Enable atomic commit mode
    .build();
```

## Auto-Replay Successes

The library supports automatic replay of successful items when a batch contains both successes and failures. This is useful for scenarios like:
- **Atomic backends** (e.g., database inserts with unique constraints) where some items are rejected
- **Backends that need reprocessing** when partial failures occur

### Configuration

Replay can be controlled in two ways:

#### 1. Backend Decision (Recommended)

Implement `shouldReplaySuccesses()` in your Backend:

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

#### 2. Config Fallback

Use `autoReplaySuccesses(true)` in BatcherConfig:
   - Only used if backend doesn't override `shouldReplaySuccesses()`
   - Default: `false`

### Behavior

- Only replays when there are **both** successes and failures in the same batch
- Successful items are re-submitted to the batcher for another attempt
- Failures are returned to clients immediately
- Backend decision takes precedence over config

## Architecture

### Virtual Threads

- Uses Java 21 virtual threads for efficient I/O-bound operations
- Backend dispatch runs on virtual threads automatically
- Blocking I/O is efficient with virtual threads

### Synchronous Backend API

The `Backend<T>` interface is simple and synchronous:
- **You don't need to return `CompletableFuture`** - just return `BatchResult` directly
- Can throw `Exception` for error handling
- Backend code can be blocking (HTTP calls, DB queries, etc.)
- Virtual threads make blocking efficient

### Non-blocking Client API

The batcher's `submit()` method returns `CompletableFuture<BatchResult<T>>`:
- Non-blocking for clients
- Can use callbacks: `future.thenAccept(result -> ...)`
- Can compose: `CompletableFuture.allOf(...)`
- Can wait if needed: `future.get()`

### Thread Safety

- All public methods are thread-safe
- Safe for concurrent use from multiple threads
- Dynamic configuration updates are thread-safe

### Resource Management

- Implements `AutoCloseable` for proper cleanup
- Gracefully shuts down on `close()`
- Processes remaining items before shutdown

## Requirements

- **Java 21+** (for virtual threads)
- **Gradle 9.2.0+** (for building)

## Building

```bash
./gradlew build
```

## Examples

See the `examples/` directory for comprehensive examples:

- **BasicUsageExample.java** - Simple batching demonstration
- **AtomicCommitExample.java** - Atomic commit mode
- **AutoReplayExample.java** - Automatic replay of successful items
- **TimeBasedBatchingExample.java** - Time-based batching
- **MetricsExample.java** - Metrics collection and monitoring
- **HttpBackendExample.java** - HTTP backend integration
- **CustomBackendReplayExample.java** - Custom backend with replay logic

### Running Examples

```bash
# Compile examples
javac -cp "$(./gradlew -q printClasspath)" examples/*.java

# Run an example
java -cp "$(./gradlew -q printClasspath):examples" \
    com.vajrapulse.vortex.example.BasicUsageExample
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
- **HTML Report**: `build/reports/jmh/html/index.html`
- **JSON Results**: `build/reports/jmh/results.json`
- **Text Results**: `build/results/jmh/results.txt`

The benchmarks measure:
- **Throughput**: Operations per second for single requests, concurrent requests, and batch submissions
- **Latency**: Average time from submission to completion

For more details, see [BENCHMARKS.md](documents/guides/BENCHMARKS.md).

## Design

For a detailed explanation of the architecture, request flow, and design decisions, see [DESIGN.md](documents/architecture/DESIGN.md).

## Best Practices

### Choosing Batch Size

- **Small batches (1-10)**: Lower latency, higher overhead
- **Medium batches (10-100)**: Good balance for most use cases
- **Large batches (100+)**: Higher throughput, higher latency

### Choosing Linger Time

- **Short (10-50ms)**: Lower latency, smaller batches
- **Medium (50-200ms)**: Good balance
- **Long (200ms+)**: Larger batches, higher latency

### Error Handling

- Use `retryableErrorPredicate` to retry only transient errors
- Set appropriate `maxRetries` to avoid infinite retry loops
- Use `retryDelay` to avoid overwhelming backends
- Monitor `vortex.requests.failed` metric

### Backpressure Handling

The library provides built-in backpressure control through configurable queue size:

**Queue Size Configuration:**
- Default: `2 × batchSize` (e.g., batchSize=10 → queue=20 items)
- Configurable via `maxQueueSize()` in `BatcherConfig`
- Must be at least equal to `batchSize`

**Backpressure Behavior:**
- When queue is full, `submit()` waits up to 100ms for space
- If still full after 100ms, returns `RejectedExecutionException`
- Monitor `vortex.queue.depth` metric to detect backpressure early

**Handling Rejections:**

```java
// Option 1: Handle in callback
CompletableFuture<Void> future = batcher.submitWithCallback(
    "item",
    (item, result) -> { /* handle result */ }
);
future.exceptionally(throwable -> {
    if (throwable.getCause() instanceof RejectedExecutionException) {
        // Queue is full - handle backpressure
        // Options: retry, log, send to dead letter queue, or fail fast
        System.err.println("Request rejected: Queue full");
    }
    return null;
});

// Option 2: Handle in submit() future
CompletableFuture<BatchResult<String>> future = batcher.submit("item");
future.exceptionally(throwable -> {
    if (throwable instanceof RejectedExecutionException) {
        // Handle backpressure
    }
    return null;
});
```

**Best Practices:**
- Set `maxQueueSize` based on expected throughput and backend capacity
- Monitor `vortex.queue.depth` to detect backpressure early
- Handle `RejectedExecutionException` appropriately (retry, circuit breaker, etc.)
- Consider increasing `maxQueueSize` for high-throughput scenarios
- Use `submitWithCallback()` for cleaner error handling

**For detailed backpressure handling strategies, see [Backpressure Guide](documents/guides/BACKPRESSURE_GUIDE.md)**

### Performance Tuning

- Enable `perItemMetrics` only when needed (adds overhead)
- Use `debugMode` only for troubleshooting (adds logging overhead)
- Monitor queue depth to detect backpressure
- Adjust batch size and linger time based on metrics

## Migration from 0.0.1

### Breaking Changes

None - 0.0.3 is backward compatible with 0.0.2 and 0.0.1.

### New Features

- `ItemResult` sealed interface for type-safe results
- `findItemResult()` method in `BatchResult`
- `submitWithCallback()` method in `MicroBatcher`
- Retry support (`maxRetries`, `retryDelay`, `retryableErrorPredicate`)
- Per-item metrics (`perItemMetrics` flag)
- Debug mode (`debugMode` flag)
- Dynamic configuration (`updateBatchSize()`, `updateLingerTime()`)
- Configurable queue size (`maxQueueSize`) for backpressure control
- Enhanced error handling methods (`isCompleteFailure()`, `getFailureRate()`, `getFailuresByType()`)

### Recommended Updates

1. Use `ItemResult` for type-safe result handling
2. Use `findItemResult()` instead of manual iteration
3. Consider enabling retry for transient failures
4. Use `submitWithCallback()` for cleaner async code
5. Configure `maxQueueSize` based on your throughput requirements
6. Always handle `RejectedExecutionException` to detect backpressure

