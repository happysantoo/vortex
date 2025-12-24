# Vortex MicroBatcher - Complete User Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Quick Start](#quick-start)
3. [Core Concepts](#core-concepts)
4. [Synchronous vs Asynchronous Usage](#synchronous-vs-asynchronous-usage)
5. [Exception Handling](#exception-handling)
6. [Backpressure and Queue Management](#backpressure-and-queue-management)
7. [Configuration Guide](#configuration-guide)
8. [Advanced Features](#advanced-features)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Introduction

Vortex MicroBatcher is a lightweight Java 21 library that groups individual requests into batches and dispatches them to your backend. It's designed for high-throughput scenarios where batching improves efficiency.

### Key Benefits

- **Immediate Rejection Feedback**: Know instantly if your item was accepted or rejected
- **Async Result Handling**: Get notified when your item is processed via callbacks
- **Built-in Backpressure**: Automatic queue management with configurable rejection thresholds
- **Type-Safe Results**: Sealed `ItemResult` interface with pattern matching support
- **Production Ready**: Comprehensive metrics, retry support, graceful shutdown

---

## Quick Start

### Basic Setup

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;
import java.time.Duration;

// 1. Define your backend
Backend<String> backend = batch -> {
    // Process the batch
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
    // Submit items
    ItemResult<String> result = batcher.submit("item-1", null);
    
    if (result instanceof ItemResult.Success<String>) {
        System.out.println("Item accepted!");
    } else if (result instanceof ItemResult.Failure<String> failure) {
        System.out.println("Item rejected: " + failure.error().getMessage());
    }
}
```

---

## Core Concepts

### 1. Submission Flow

When you call `submit(item, callback)`:

1. **Immediate Check**: The method returns immediately with `ItemResult.Success` or `ItemResult.Failure`
2. **Queueing**: If accepted, the item is queued for batch processing
3. **Batching**: Items are grouped into batches based on:
   - **Batch Size**: When queue reaches `batchSize` items
   - **Linger Time**: When `lingerTime` duration elapses (whichever comes first)
4. **Dispatch**: The batch is sent to your backend
5. **Callback**: If provided, your callback is invoked with the item's individual result

### 2. ItemResult Types

`ItemResult<T>` is a sealed interface with two variants:

```java
// Success - item was processed successfully
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

---

## Synchronous vs Asynchronous Usage

### Synchronous Usage (Fire and Forget)

Use this when you only need to know if the item was **accepted** (not rejected immediately):

```java
ItemResult<String> result = batcher.submit("item", null);

if (result instanceof ItemResult.Failure<String> failure) {
    // Item was rejected immediately (queue full, etc.)
    handleRejection(failure.error());
}
// Item accepted - will be processed later in a batch
// No callback = no notification when processing completes
```

**When to Use:**
- You don't need to know the processing result
- You only care about immediate acceptance/rejection
- Fire-and-forget scenarios

### Asynchronous Usage (With Callback)

Use this when you need to know the **processing result**:

```java
batcher.submit("item", (item, result) -> {
    if (result instanceof ItemResult.Success<String>) {
        System.out.println("Item processed successfully: " + item);
    } else if (result instanceof ItemResult.Failure<String> failure) {
        System.err.println("Item failed: " + failure.error().getMessage());
    }
});
```

**When to Use:**
- You need to know if processing succeeded or failed
- You need to handle individual item results
- You're building request/response systems

### Complete Example: Sync + Async

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    AtomicInteger accepted = new AtomicInteger(0);
    AtomicInteger rejected = new AtomicInteger(0);
    AtomicInteger processed = new AtomicInteger(0);
    
    for (int i = 0; i < 100; i++) {
        String item = "item-" + i;
        
        // Submit with callback for async result handling
        ItemResult<String> immediateResult = batcher.submit(item, (submittedItem, result) -> {
            // This callback fires when the item is processed (async)
            processed.incrementAndGet();
            
            if (result instanceof ItemResult.Success<String>) {
                System.out.println("✓ " + submittedItem + " processed successfully");
            } else if (result instanceof ItemResult.Failure<String> failure) {
                System.err.println("✗ " + submittedItem + " failed: " + failure.error().getMessage());
            }
        });
        
        // Check immediate acceptance/rejection (sync)
        if (immediateResult instanceof ItemResult.Success<String>) {
            accepted.incrementAndGet();
        } else if (immediateResult instanceof ItemResult.Failure<String>) {
            rejected.incrementAndGet();
        }
    }
    
    // Wait for all processing to complete
    batcher.awaitCompletion(5, TimeUnit.SECONDS);
    
    System.out.println("Accepted: " + accepted.get());
    System.out.println("Rejected: " + rejected.get());
    System.out.println("Processed: " + processed.get());
}
```

### Using submitAsync for CompletableFuture-Based Workflows

The `submitAsync` method returns `CompletableFuture<ItemResult<T>>`, making it ideal for async/await-style programming and composing complex async workflows:

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
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
                // Handle immediate rejection (queue full)
                handleRejection(throwable);
            }
            return null;
        });
}
```

**Key Differences from `submit`:**

| Feature | `submit` | `submitAsync` |
|---------|----------|--------------|
| Return Type | `ItemResult<T>` (synchronous) | `CompletableFuture<ItemResult<T>>` (async) |
| Immediate Rejections | Returns `ItemResult.Failure` | Completes future exceptionally |
| Chaining | Limited (callback-based) | Full CompletableFuture API |
| Best For | Simple fire-and-forget, callbacks | Complex async workflows, composition |

**Example: Batch Processing with CompletableFuture.allOf()**

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    List<String> items = List.of("item-1", "item-2", "item-3", "item-4", "item-5");
    
    // Submit all items asynchronously
    List<CompletableFuture<ItemResult<String>>> futures = items.stream()
        .map(batcher::submitAsync)
        .toList();
    
    // Wait for all items to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenRun(() -> {
            System.out.println("All items processed!");
            
            // Process results
            futures.forEach(future -> {
                try {
                    ItemResult<String> result = future.get();
                    if (result instanceof ItemResult.Success<String>) {
                        System.out.println("Success: " + result.getItem());
                    } else if (result instanceof ItemResult.Failure<String> failure) {
                        System.err.println("Failed: " + failure.error().getMessage());
                    }
                } catch (Exception e) {
                    // Handle exceptions (e.g., ItemRejectedException)
                    System.err.println("Exception: " + e.getMessage());
                }
            });
        })
        .get(10, TimeUnit.SECONDS); // Wait with timeout
}
```

**Example: Error Handling with submitAsync**

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    batcher.submitAsync("item")
        .thenAccept(result -> {
            if (result instanceof ItemResult.Success<String>) {
                System.out.println("Success: " + result.getItem());
            } else if (result instanceof ItemResult.Failure<String> failure) {
                System.err.println("Processing failed: " + failure.error().getMessage());
            }
        })
        .exceptionally(throwable -> {
            if (throwable instanceof ItemRejectedException rejected) {
                // Item was rejected immediately (queue full)
                System.err.println("Item rejected: " + rejected.getMessage());
                System.err.println("Source: " + rejected.getSourceName());
                System.err.println("Current/Max: " + rejected.getCurrentLevel() + "/" + rejected.getMaxLevel());
            } else if (throwable.getCause() instanceof IllegalStateException) {
                // Batcher is closed
                System.err.println("Batcher is closed");
            } else {
                // Unexpected error
                System.err.println("Unexpected error: " + throwable.getMessage());
            }
            return null;
        });
}
```

**When to Use `submitAsync`:**

- ✅ You need to compose multiple async operations
- ✅ You want to use `CompletableFuture.allOf()` or `anyOf()`
- ✅ You prefer async/await-style programming
- ✅ You need to chain transformations with `thenApply()`, `thenCompose()`, etc.
- ✅ You're building reactive pipelines

**When to Use `submit`:**

- ✅ You need immediate acceptance/rejection feedback
- ✅ You prefer callback-based async handling
- ✅ You want simpler, more straightforward code
- ✅ You're building request/response systems

---

## Exception Handling

### Exception Types

#### 1. ItemRejectedException

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

**Factory Methods:**

```java
// Queue full
ItemRejectedException.queueFull(currentSize, maxSize);

// Concurrent limit reached
ItemRejectedException.concurrentLimitReached(activeBatches, maxBatches);
```

#### 2. IllegalStateException

Thrown when the batcher is closed:

```java
batcher.close();

// This will throw IllegalStateException
batcher.submit("item", null);
```

**Handling:**

```java
try {
    batcher.submit("item", null);
} catch (IllegalStateException e) {
    // Batcher is closed - cannot submit new items
    logger.warn("Cannot submit item: batcher is closed");
}
```

#### 3. NullPointerException

Thrown when submitting a null item:

```java
try {
    batcher.submit(null, null);
} catch (NullPointerException e) {
    // Item cannot be null
    logger.error("Cannot submit null item", e);
}
```

#### 4. Exceptions in Callbacks

Exceptions thrown in callbacks are logged but don't affect batch processing:

```java
batcher.submit("item", (item, result) -> {
    // If this throws an exception, it's logged but doesn't affect the batch
    processResult(result); // May throw
});
```

**Best Practice:** Wrap callback logic in try-catch:

```java
batcher.submit("item", (item, result) -> {
    try {
        processResult(result);
    } catch (Exception e) {
        logger.error("Error processing result for item: " + item, e);
    }
});
```

### Complete Exception Handling Example

```java
try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
    for (String item : items) {
        try {
            ItemResult<String> result = batcher.submit(item, itemResult -> {
                try {
                    if (itemResult instanceof ItemResult.Success<String> success) {
                        handleSuccess(success.getItem());
                    } else if (itemResult instanceof ItemResult.Failure<String> failure) {
                        handleFailure(submittedItem, failure.error());
                    }
                } catch (Exception e) {
                    logger.error("Error in callback for item: " + submittedItem, e);
                }
            });
            
            // Check immediate rejection
            if (result instanceof ItemResult.Failure<String> failure) {
                Throwable error = failure.error();
                
                if (error instanceof ItemRejectedException rejected) {
                    handleRejection(item, rejected);
                } else if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    handleInterruption(item);
                } else {
                    handleUnknownError(item, error);
                }
            }
        } catch (IllegalStateException e) {
            // Batcher is closed
            logger.warn("Cannot submit item: batcher is closed", e);
            break;
        } catch (NullPointerException e) {
            // Null item
            logger.error("Cannot submit null item", e);
        }
    }
}
```

---

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

- **`maxQueueSize`**: Maximum number of items that can be queued
  - Default: `2 * batchSize`
  - When queue reaches this size, new items are rejected immediately

- **`queueRejectionThreshold`**: Percentage (0.0 to 1.0) at which to start rejecting
  - Default: `1.0` (100% - reject only when completely full)
  - Example: `0.8` means reject when queue is 80% full
  - Useful for proactive backpressure

### Backpressure Scenarios

#### 1. Queue Full Rejection

```java
// Queue is full - item is rejected immediately
ItemResult<String> result = batcher.submit("item", null);

if (result instanceof ItemResult.Failure<String> failure) {
    if (failure.error() instanceof ItemRejectedException rejected) {
        if ("Vortex Queue Depth".equals(rejected.getSourceName())) {
            // Queue is full
            int currentSize = rejected.getCurrentLevel();
            int maxSize = rejected.getMaxLevel();
            
            System.out.println("Queue full: " + currentSize + "/" + maxSize);
            
            // Options:
            // 1. Retry later
            retryLater(item);
            
            // 2. Send to overflow/dead letter queue
            sendToOverflow(item);
            
            // 3. Fail fast
            throw new RuntimeException("Queue full, cannot process");
        }
    }
}
```

#### 2. Concurrent Batch Limit Rejection

```java
BatcherConfig config = BatcherConfig.builder()
    .maxConcurrentBatches(5)  // Limit to 5 concurrent batches
    .build();

// If 5 batches are already in flight, new batches are rejected
ItemResult<String> result = batcher.submit("item", null);

if (result instanceof ItemResult.Failure<String> failure) {
    if (failure.error() instanceof ItemRejectedException rejected) {
        if ("Concurrent Batches".equals(rejected.getSourceName())) {
            int activeBatches = rejected.getCurrentLevel();
            int maxBatches = rejected.getMaxLevel();
            
            System.out.println("Too many concurrent batches: " + activeBatches + "/" + maxBatches);
            
            // Wait and retry
            Thread.sleep(100);
            retrySubmission(item);
        }
    }
}
```

### Backpressure Handling Strategies

#### Strategy 1: Retry with Exponential Backoff

```java
public void submitWithRetry(String item, MicroBatcher<String> batcher, int maxRetries) {
    for (int attempt = 0; attempt < maxRetries; attempt++) {
        ItemResult<String> result = batcher.submit(item, null);
        
        if (result instanceof ItemResult.Success<String>) {
            return; // Success
        }
        
        if (result instanceof ItemResult.Failure<String> failure) {
            if (failure.error() instanceof ItemRejectedException) {
                // Backpressure - wait and retry
                long delay = (long) Math.pow(2, attempt) * 100; // Exponential backoff
                Thread.sleep(delay);
                continue;
            } else {
                // Other error - don't retry
                throw new RuntimeException("Submission failed", failure.error());
            }
        }
    }
    
    throw new RuntimeException("Max retries exceeded");
}
```

#### Strategy 2: Overflow Queue

```java
private final BlockingQueue<String> overflowQueue = new LinkedBlockingQueue<>();

public void submitWithOverflow(String item, MicroBatcher<String> batcher) {
    ItemResult<String> result = batcher.submit(item, null);
    
    if (result instanceof ItemResult.Failure<String> failure) {
        if (failure.error() instanceof ItemRejectedException) {
            // Backpressure - send to overflow
            if (overflowQueue.offer(item)) {
                logger.warn("Item sent to overflow queue: " + item);
            } else {
                // Overflow queue also full - fail
                throw new RuntimeException("Both main and overflow queues full");
            }
        } else {
            throw new RuntimeException("Submission failed", failure.error());
        }
    }
}

// Process overflow queue when main queue has capacity
public void processOverflow(MicroBatcher<String> batcher) {
    while (!overflowQueue.isEmpty()) {
        String item = overflowQueue.poll();
        if (item != null) {
            ItemResult<String> result = batcher.submit(item, null);
            if (result instanceof ItemResult.Success<String>) {
                logger.info("Item moved from overflow to main queue: " + item);
            } else {
                // Still full - put back
                overflowQueue.offer(item);
                break;
            }
        }
    }
}
```

#### Strategy 3: Fail Fast

```java
public void submitFailFast(String item, MicroBatcher<String> batcher) {
    ItemResult<String> result = batcher.submit(item, null);
    
    if (result instanceof ItemResult.Failure<String> failure) {
        if (failure.error() instanceof ItemRejectedException) {
            // Backpressure - fail immediately
            throw new RuntimeException("System overloaded, cannot process item", failure.error());
        } else {
            throw new RuntimeException("Submission failed", failure.error());
        }
    }
}
```

### Monitoring Backpressure

Use metrics to monitor backpressure:

```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Check queue depth
int queueDepth = batcher.getQueueDepth();
int maxQueueSize = batcher.getConfig().getMaxQueueSize();
double queueUtilization = (double) queueDepth / maxQueueSize;

if (queueUtilization > 0.8) {
    logger.warn("High queue utilization: {}%", queueUtilization * 100);
}

// Check rejection rate
double rejectionRate = metrics.getRejectionRate();
if (rejectionRate > 0.1) {
    logger.warn("High rejection rate: {}%", rejectionRate * 100);
}
```

---

## Configuration Guide

### Basic Configuration

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                           // Items per batch
    .lingerTime(Duration.ofMillis(100))      // Max wait time
    .maxQueueSize(50)                        // Queue capacity
    .build();
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

### Configuration Presets

Use presets for common scenarios:

```java
// High Throughput (large batches, longer wait)
BatcherConfig highThroughput = BatcherConfig.highThroughputPreset();

// Low Latency (small batches, short wait)
BatcherConfig lowLatency = BatcherConfig.lowLatencyPreset();

// Balanced (default)
BatcherConfig balanced = BatcherConfig.balancedPreset();

// Resilient (with retry)
Predicate<Throwable> retryable = e -> e instanceof IOException;
BatcherConfig resilient = BatcherConfig.resilientPreset(retryable);
```

---

## Advanced Features

### 1. Retry Support

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

### 2. Atomic Commit Mode

In atomic commit mode, if any item in a batch fails, the entire batch is considered failed:

```java
BatcherConfig config = BatcherConfig.builder()
    .atomicCommit(true)  // Enable atomic commit
    .build();

// If any item fails, all items in the batch fail
batcher.submit("item-1", (item, result) -> {
    // All items in the batch will have the same result
});
```

### 3. Auto-Replay Successes

When a batch has mixed results, successful items can be automatically replayed:

```java
BatcherConfig config = BatcherConfig.builder()
    .autoReplaySuccesses(true)  // Replay successful items
    .build();

// If batch has mixed results, successful items are replayed in a new batch
```

### 4. Graceful Shutdown

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
    // It will:
    // 1. Stop accepting new items
    // 2. Wait for queue to drain
    // 3. Wait for in-flight batches to complete
    // 4. Process any remaining items synchronously
}
```

### 5. Metrics and Monitoring

```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Queue metrics
int queueDepth = batcher.getQueueDepth();

// Rate metrics
double submissionRate = metrics.getSubmissionRate();
double successRate = metrics.getSuccessRate();
double failureRate = metrics.getFailureRate();
double rejectionRate = metrics.getRejectionRate();

// Latency metrics
double avgLatency = metrics.getAverageLatency();
double p95Latency = metrics.getPercentileLatency(0.95);
double p99Latency = metrics.getPercentileLatency(0.99);

// Health check
if (failureRate > 0.1 || rejectionRate > 0.1) {
    logger.warn("System health degraded");
}
```

### 6. Diagnostics

```java
BatcherDiagnostics diagnostics = batcher.diagnostics();

System.out.println("Closed: " + diagnostics.isClosed());
System.out.println("Batch Size: " + diagnostics.getCurrentBatchSize());
System.out.println("Linger Time: " + diagnostics.getCurrentLingerTime());
System.out.println("Queue Depth: " + diagnostics.getQueueDepth());
```

---

## Best Practices

### 1. Always Handle Rejections

```java
// ❌ Bad - ignores rejections
batcher.submit("item", null);

// ✅ Good - handles rejections
ItemResult<String> result = batcher.submit("item", null);
if (result instanceof ItemResult.Failure<String> failure) {
    handleRejection(failure.error());
}
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

### 4. Monitor Queue Depth

```java
// ✅ Good - proactive monitoring
int queueDepth = batcher.getQueueDepth();
if (queueDepth > threshold) {
    logger.warn("High queue depth: {}", queueDepth);
    // Take action: reduce submission rate, scale up, etc.
}
```

### 5. Configure Appropriate Batch Sizes

```java
// High throughput scenario
BatcherConfig config = BatcherConfig.builder()
    .batchSize(100)                    // Large batches
    .lingerTime(Duration.ofMillis(500)) // Longer wait
    .build();

// Low latency scenario
BatcherConfig config = BatcherConfig.builder()
    .batchSize(5)                      // Small batches
    .lingerTime(Duration.ofMillis(10))  // Short wait
    .build();
```

### 6. Use Presets for Common Scenarios

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

---

## Troubleshooting

### Problem: High Rejection Rate

**Symptoms:**
- Many items are rejected immediately
- `ItemRejectedException` with source "Vortex Queue Depth"

**Solutions:**
1. **Increase Queue Size:**
   ```java
   .maxQueueSize(200)  // Increase from default
   ```

2. **Increase Batch Size:**
   ```java
   .batchSize(50)  // Process more items per batch
   ```

3. **Reduce Submission Rate:**
   ```java
   // Add delay between submissions
   Thread.sleep(10);
   ```

4. **Use Backpressure Threshold:**
   ```java
   .queueRejectionThreshold(0.9)  // Reject at 90% instead of 100%
   ```

### Problem: High Latency

**Symptoms:**
- Items take a long time to process
- High `avgLatency` in metrics

**Solutions:**
1. **Reduce Linger Time:**
   ```java
   .lingerTime(Duration.ofMillis(50))  // Process batches faster
   ```

2. **Reduce Batch Size:**
   ```java
   .batchSize(5)  // Smaller batches = faster processing
   ```

3. **Increase Concurrent Batches:**
   ```java
   .maxConcurrentBatches(10)  // Process more batches in parallel
   ```

### Problem: Items Not Being Processed

**Symptoms:**
- Items are accepted but callbacks never fire
- Queue depth stays high

**Solutions:**
1. **Check Backend:**
   ```java
   // Ensure backend is not blocking or throwing exceptions
   Backend<String> backend = batch -> {
       try {
           return processBatch(batch);
       } catch (Exception e) {
           logger.error("Backend error", e);
           throw e;  // Let MicroBatcher handle retry
       }
   };
   ```

2. **Check Batcher is Not Closed:**
   ```java
   if (batcher.isClosed()) {
       logger.error("Batcher is closed!");
   }
   ```

3. **Wait for Processing:**
   ```java
   batcher.awaitCompletion(5, TimeUnit.SECONDS);
   ```

### Problem: Memory Issues

**Symptoms:**
- OutOfMemoryError
- High memory usage

**Solutions:**
1. **Reduce Queue Size:**
   ```java
   .maxQueueSize(50)  // Limit queue capacity
   ```

2. **Reduce Batch Size:**
   ```java
   .batchSize(10)  // Smaller batches = less memory
   ```

3. **Enable Backpressure Threshold:**
   ```java
   .queueRejectionThreshold(0.8)  // Reject earlier
   ```

---

## Complete Example

Here's a complete example combining all concepts:

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.results.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CompleteExample {
    public static void main(String[] args) throws Exception {
        // 1. Define backend
        Backend<String> backend = batch -> {
            System.out.println("Processing batch of " + batch.size() + " items");
            
            List<SuccessEvent<String>> successes = batch.stream()
                .map(SuccessEvent::new)
                .toList();
            
            return new BatchResult<>(successes, List.of());
        };
        
        // 2. Configure batcher
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(50)
            .queueRejectionThreshold(0.9)
            .maxConcurrentBatches(5)
            .maxRetries(3)
            .retryDelay(Duration.ofMillis(100))
            .retryableErrorPredicate(e -> e instanceof IOException)
            .build();
        
        // 3. Create batcher
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            AtomicInteger accepted = new AtomicInteger(0);
            AtomicInteger rejected = new AtomicInteger(0);
            AtomicInteger processed = new AtomicInteger(0);
            AtomicInteger failed = new AtomicInteger(0);
            
            // 4. Submit items with callbacks
            for (int i = 0; i < 100; i++) {
                String item = "item-" + i;
                
                try {
                    ItemResult<String> result = batcher.submit(item, itemResult -> {
                        // Async callback - fires when item is processed
                        processed.incrementAndGet();
                        
                        if (itemResult instanceof ItemResult.Success<String> success) {
                            System.out.println("✓ " + success.getItem() + " succeeded");
                        } else if (itemResult instanceof ItemResult.Failure<String> failure) {
                            failed.incrementAndGet();
                            System.err.println("✗ " + submittedItem + " failed: " + 
                                failure.error().getMessage());
                        }
                    });
                    
                    // Check immediate acceptance/rejection
                    if (result instanceof ItemResult.Success<String>) {
                        accepted.incrementAndGet();
                    } else if (result instanceof ItemResult.Failure<String> failure) {
                        rejected.incrementAndGet();
                        Throwable error = failure.error();
                        
                        if (error instanceof ItemRejectedException rejectedEx) {
                            System.err.println("⚠ " + item + " rejected: " + 
                                rejectedEx.getSourceName() + " - " + rejectedEx.getMessage());
                            // Handle rejection: retry, overflow queue, etc.
                        }
                    }
                } catch (IllegalStateException e) {
                    System.err.println("Batcher closed, stopping submission");
                    break;
                }
                
                // Small delay to avoid overwhelming the queue
                Thread.sleep(10);
            }
            
            // 5. Wait for all processing to complete
            boolean completed = batcher.awaitCompletion(10, TimeUnit.SECONDS);
            
            System.out.println("\n=== Summary ===");
            System.out.println("Accepted: " + accepted.get());
            System.out.println("Rejected: " + rejected.get());
            System.out.println("Processed: " + processed.get());
            System.out.println("Failed: " + failed.get());
            System.out.println("Completed: " + completed);
            
            // 6. Check metrics
            MetricsProvider metrics = batcher.getMetricsProvider();
            System.out.println("\n=== Metrics ===");
            System.out.println("Success Rate: " + metrics.getSuccessRate());
            System.out.println("Failure Rate: " + metrics.getFailureRate());
            System.out.println("Rejection Rate: " + metrics.getRejectionRate());
            System.out.println("Avg Latency: " + metrics.getAverageLatency() + "ms");
        }
    }
}
```

---

## Summary

- **Synchronous**: Use `submit(item, null)` when you only need immediate acceptance/rejection feedback
- **Asynchronous**: Use `submit(item, callback)` when you need to know the processing result
- **Exceptions**: Always handle `ItemRejectedException` for backpressure, `IllegalStateException` for closed batcher
- **Backpressure**: Configure `maxQueueSize` and `queueRejectionThreshold` appropriately
- **Best Practices**: Use try-with-resources, handle callback exceptions, monitor queue depth
- **Configuration**: Use presets for common scenarios, tune batch size and linger time for your use case

For more examples, see the `examples/` directory in the repository.

