# Backpressure Handling Guide

**Version**: 0.0.3  
**Last Updated**: 2024

## Table of Contents

1. [Introduction](#introduction)
2. [Understanding Backpressure](#understanding-backpressure)
3. [How Vortex Handles Backpressure](#how-vortex-handles-backpressure)
4. [Configuration](#configuration)
5. [Detection and Handling](#detection-and-handling)
6. [Strategies for Managing Backpressure](#strategies-for-managing-backpressure)
7. [Monitoring and Metrics](#monitoring-and-metrics)
8. [Best Practices](#best-practices)
9. [Common Patterns](#common-patterns)
10. [Troubleshooting](#troubleshooting)

## Introduction

**Backpressure** occurs when the rate of incoming requests exceeds the rate at which the system can process them. In the context of Vortex, backpressure happens when the internal batching queue reaches its maximum capacity (`maxQueueSize`), and new submissions cannot be accepted.

This guide explains:
- What backpressure is and why it matters
- How Vortex detects and signals backpressure
- Strategies for handling backpressure gracefully
- Best practices for preventing and managing backpressure

## Understanding Backpressure

### What is Backpressure?

Backpressure is a flow control mechanism that prevents a system from being overwhelmed by incoming requests. When a system cannot keep up with the incoming load, it needs a way to signal that it's at capacity and cannot accept more work.

### Why Backpressure Matters

Without proper backpressure handling:

1. **Memory Exhaustion**: Unbounded queues can consume all available memory
2. **Degraded Performance**: Overloaded systems process requests more slowly
3. **Cascading Failures**: One overloaded component can bring down the entire system
4. **Poor User Experience**: Requests may hang indefinitely or fail unpredictably

### Backpressure in Micro-Batching Context

In Vortex, backpressure occurs when:
- The batching queue is full (reached `maxQueueSize`)
- New items cannot be queued for batching
- The system needs to signal rejection to the caller

## How Vortex Handles Backpressure

### Queue-Based Backpressure

Vortex uses a bounded `LinkedBlockingQueue` with a configurable maximum size:

```java
// Queue size is configurable via BatcherConfig.maxQueueSize
// Default: 2x batchSize
this.queue = new LinkedBlockingQueue<>(config.getMaxQueueSize());
```

### Submission Behavior

When `submit()` is called:

1. **Queue Not Full**: Item is queued successfully, returns a `CompletableFuture`
2. **Queue Full**: 
   - Attempts to offer the item with a 100ms timeout
   - If still full after timeout, returns a `CompletableFuture` that completes exceptionally with `RejectedExecutionException`

```java
// From MicroBatcher.java
if (!queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
    future.completeExceptionally(new RejectedExecutionException("Queue is full"));
    return future;
}
```

### Key Characteristics

- **Non-Blocking**: `submit()` never blocks the calling thread
- **Fast Failure**: Rejection happens quickly (100ms timeout)
- **Async Notification**: Rejection is signaled via `CompletableFuture` exception
- **Thread-Safe**: Safe to call from multiple threads concurrently

## Configuration

### Setting maxQueueSize

The `maxQueueSize` parameter controls the maximum number of pending requests:

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)                    // Process 10 items per batch
    .lingerTime(Duration.ofMillis(100))
    .maxQueueSize(50)                 // Allow up to 50 pending items
    .build();
```

### Default Behavior

If `maxQueueSize` is not specified:
- **Default**: `2 * batchSize`
- **Minimum**: Must be at least equal to `batchSize`

```java
// Default calculation
this.maxQueueSize = builder.maxQueueSize != null 
    ? builder.maxQueueSize 
    : builder.batchSize * 2;
```

### Choosing the Right Queue Size

Consider these factors:

1. **Throughput Requirements**: How many requests per second do you need to handle?
2. **Processing Latency**: How long does each batch take to process?
3. **Memory Constraints**: Larger queues use more memory
4. **Backpressure Tolerance**: How should your system behave when overloaded?

**Rule of Thumb**:
```
maxQueueSize = (expected_requests_per_second * average_batch_processing_time_seconds) + batch_size
```

**Example**:
- 1000 requests/second
- 50ms average batch processing time
- Batch size: 10

```
maxQueueSize = (1000 * 0.05) + 10 = 60
```

## Detection and Handling

### Detecting Backpressure

Backpressure is signaled via `RejectedExecutionException`:

```java
CompletableFuture<BatchResult<String>> future = batcher.submit(item);

future.whenComplete((result, throwable) -> {
    if (throwable instanceof RejectedExecutionException) {
        // Backpressure detected!
        System.out.println("Queue is full: " + throwable.getMessage());
    } else if (throwable != null) {
        // Other error
        System.out.println("Error: " + throwable.getMessage());
    } else {
        // Success
        System.out.println("Processed successfully");
    }
});
```

### Handling Rejection

When backpressure is detected, you have several options:

#### 1. Immediate Failure

```java
future.whenComplete((result, throwable) -> {
    if (throwable instanceof RejectedExecutionException) {
        // Log and fail immediately
        logger.error("Request rejected due to backpressure", throwable);
        // Return error to caller
    }
});
```

#### 2. Retry with Backoff

```java
private void submitWithRetry(MicroBatcher<String> batcher, String item, int attempt) {
    CompletableFuture<BatchResult<String>> future = batcher.submit(item);
    
    future.whenComplete((result, throwable) -> {
        if (throwable instanceof RejectedExecutionException && attempt < 3) {
            // Exponential backoff
            long backoffMs = 50L * (1L << attempt);
            scheduleRetry(() -> submitWithRetry(batcher, item, attempt + 1), backoffMs);
        }
    });
}
```

#### 3. Fallback Behavior

```java
future.whenComplete((result, throwable) -> {
    if (throwable instanceof RejectedExecutionException) {
        // Use fallback mechanism
        fallbackProcessor.process(item);
    }
});
```

## Strategies for Managing Backpressure

### 1. Proactive Monitoring

Monitor queue depth before submitting:

```java
MetricsProvider metrics = batcher.getMetricsProvider();
int queueDepth = metrics.getQueueDepth();
int maxQueueSize = config.getMaxQueueSize();
double utilization = (double) queueDepth / maxQueueSize;

if (utilization > 0.8) {
    // Queue is 80% full - slow down submissions
    Thread.sleep(50);
} else if (utilization > 0.5) {
    // Queue is 50% full - reduce rate slightly
    Thread.sleep(10);
}

batcher.submit(item);
```

### 2. Rate Limiting

Limit submission rate to prevent queue overflow:

```java
RateLimiter rateLimiter = new RateLimiter(100); // 100 requests/second

if (rateLimiter.tryAcquire()) {
    batcher.submit(item);
} else {
    // Rate limit exceeded - wait or skip
}
```

### 3. Circuit Breaker Pattern

Open circuit when backpressure is too high:

```java
class CircuitBreaker {
    private int consecutiveRejections = 0;
    private boolean open = false;
    
    void recordRejection() {
        consecutiveRejections++;
        if (consecutiveRejections > 5) {
            open = true; // Open circuit
        }
    }
    
    boolean isOpen() {
        return open;
    }
}
```

### 4. Adaptive Batching

Adjust batch size based on queue depth:

```java
MetricsProvider metrics = batcher.getMetricsProvider();
int queueDepth = metrics.getQueueDepth();

if (queueDepth > config.getMaxQueueSize() * 0.8) {
    // Increase batch size to process faster
    batcher.updateBatchSize(config.getBatchSize() * 2);
} else if (queueDepth < config.getMaxQueueSize() * 0.2) {
    // Decrease batch size for lower latency
    batcher.updateBatchSize(config.getBatchSize());
}
```

### 5. Load Shedding

Drop low-priority requests when overloaded:

```java
if (metrics.getQueueDepth() > config.getMaxQueueSize() * 0.9) {
    if (isLowPriority(request)) {
        // Drop low-priority requests
        return CompletableFuture.failedFuture(
            new RejectedExecutionException("Load shedding: low priority request dropped")
        );
    }
}
```

## Monitoring and Metrics

### Key Metrics

Vortex provides several metrics for monitoring backpressure:

#### Queue Depth

```java
MetricsProvider metrics = batcher.getMetricsProvider();
int queueDepth = metrics.getQueueDepth();
```

- **Metric Name**: `vortex.queue.depth`
- **Type**: Gauge
- **Use**: Monitor current queue utilization

#### Failure Rate

```java
double failureRate = metrics.getFailureRate();
```

- **Metric Name**: `vortex.requests.failed` (counter)
- **Type**: Counter
- **Use**: Track rejection frequency

#### Request Counts

```java
long submitted = metrics.getTotalSubmitted();
long succeeded = metrics.getTotalSucceeded();
long failed = metrics.getTotalFailed();
```

### Monitoring Dashboard

Create alerts based on:

1. **Queue Utilization**: Alert when > 80%
   ```
   queue_depth / max_queue_size > 0.8
   ```

2. **Rejection Rate**: Alert when > 5%
   ```
   rejected_requests / total_requests > 0.05
   ```

3. **Sustained High Queue Depth**: Alert when > 70% for > 1 minute

### Example Monitoring Code

```java
SimpleMeterRegistry registry = new SimpleMeterRegistry();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry);

// Monitor queue depth
Gauge.builder("queue.utilization", () -> {
    MetricsProvider metrics = batcher.getMetricsProvider();
    return (double) metrics.getQueueDepth() / config.getMaxQueueSize();
})
.register(registry);

// Monitor rejection rate
Counter rejections = Counter.builder("rejections")
    .register(registry);

batcher.submit(item).whenComplete((result, throwable) -> {
    if (throwable instanceof RejectedExecutionException) {
        rejections.increment();
    }
});
```

## Best Practices

### 1. Always Handle RejectedExecutionException

Never ignore rejections:

```java
// ❌ BAD: Ignores rejection
batcher.submit(item);

// ✅ GOOD: Handles rejection
batcher.submit(item).whenComplete((result, throwable) -> {
    if (throwable instanceof RejectedExecutionException) {
        handleBackpressure(item);
    }
});
```

### 2. Set Appropriate Queue Size

- Too small: Frequent rejections, poor throughput
- Too large: High memory usage, delayed backpressure signals

**Recommendation**: Start with `2 * batchSize`, adjust based on metrics.

### 3. Monitor Proactively

Don't wait for rejections - monitor queue depth:

```java
if (metrics.getQueueDepth() > threshold) {
    // Take action before queue fills up
}
```

### 4. Implement Graceful Degradation

Have a fallback strategy:

```java
if (backpressureDetected) {
    // Option 1: Retry later
    // Option 2: Use alternative processing
    // Option 3: Drop low-priority requests
    // Option 4: Return cached result
}
```

### 5. Test Backpressure Scenarios

Test your system under load:

```java
@Test
void testBackpressureHandling() {
    // Submit more items than queue can handle
    for (int i = 0; i < 1000; i++) {
        batcher.submit("item-" + i);
    }
    
    // Verify rejections are handled correctly
    // Verify system recovers when load decreases
}
```

### 6. Use Metrics for Tuning

Monitor and adjust based on real metrics:

- If rejections are frequent → increase `maxQueueSize` or `batchSize`
- If queue is rarely full → consider reducing `maxQueueSize` to save memory
- If processing is slow → optimize backend or increase batch size

## Common Patterns

### Pattern 1: Retry with Exponential Backoff

```java
private void submitWithRetry(MicroBatcher<String> batcher, String item, int attempt) {
    batcher.submit(item).whenComplete((result, throwable) -> {
        if (throwable instanceof RejectedExecutionException && attempt < 3) {
            long backoffMs = 50L * (1L << attempt);
            scheduleRetry(() -> submitWithRetry(batcher, item, attempt + 1), backoffMs);
        }
    });
}
```

### Pattern 2: Circuit Breaker

```java
class BatcherCircuitBreaker {
    private int consecutiveRejections = 0;
    private boolean open = false;
    
    boolean shouldSubmit() {
        if (open) {
            // Check if we should try again
            if (consecutiveRejections < 10) {
                return false; // Still open
            }
            open = false; // Half-open
        }
        return true;
    }
    
    void recordRejection() {
        consecutiveRejections++;
        if (consecutiveRejections > 5) {
            open = true;
        }
    }
    
    void recordSuccess() {
        consecutiveRejections = 0;
        open = false;
    }
}
```

### Pattern 3: Adaptive Rate Limiting

```java
class AdaptiveRateLimiter {
    private double currentRate;
    private final double minRate;
    private final double maxRate;
    
    boolean tryAcquire() {
        // Adjust rate based on queue depth
        MetricsProvider metrics = batcher.getMetricsProvider();
        double utilization = (double) metrics.getQueueDepth() / maxQueueSize;
        
        if (utilization > 0.8) {
            currentRate *= 0.9; // Reduce rate
        } else if (utilization < 0.3) {
            currentRate *= 1.1; // Increase rate
        }
        
        currentRate = Math.max(minRate, Math.min(maxRate, currentRate));
        return rateLimiter.tryAcquire();
    }
}
```

### Pattern 4: Priority Queue with Load Shedding

```java
enum Priority { HIGH, MEDIUM, LOW }

class PriorityBatcher {
    void submit(String item, Priority priority) {
        MetricsProvider metrics = batcher.getMetricsProvider();
        double utilization = (double) metrics.getQueueDepth() / maxQueueSize;
        
        if (utilization > 0.9 && priority == Priority.LOW) {
            // Drop low-priority requests when queue is 90% full
            return;
        }
        
        batcher.submit(item);
    }
}
```

## Troubleshooting

### Problem: Frequent Rejections

**Symptoms**:
- High rejection rate
- `RejectedExecutionException` in logs

**Possible Causes**:
1. Queue size too small
2. Backend processing too slow
3. Submission rate too high

**Solutions**:
1. Increase `maxQueueSize`
2. Optimize backend processing
3. Implement rate limiting
4. Increase batch size to process faster

### Problem: High Memory Usage

**Symptoms**:
- High memory consumption
- GC pressure

**Possible Causes**:
1. Queue size too large
2. Items are large objects
3. Slow processing causing queue buildup

**Solutions**:
1. Reduce `maxQueueSize`
2. Use smaller item representations
3. Optimize backend processing
4. Implement load shedding

### Problem: Delayed Backpressure Detection

**Symptoms**:
- Queue fills up before rejections occur
- System becomes unresponsive

**Possible Causes**:
1. Queue size too large
2. No proactive monitoring

**Solutions**:
1. Reduce `maxQueueSize` for faster backpressure signals
2. Implement proactive monitoring
3. Use metrics to detect issues early

### Problem: System Not Recovering

**Symptoms**:
- Queue stays full even after load decreases
- Rejections continue

**Possible Causes**:
1. Backend is stuck or very slow
2. Batch processing is failing
3. Retry logic causing queue buildup

**Solutions**:
1. Check backend health
2. Review error logs
3. Implement circuit breaker
4. Add timeout handling

## Summary

Backpressure is a critical aspect of building resilient systems. Vortex provides:

- **Bounded Queue**: Configurable `maxQueueSize` prevents unbounded memory growth
- **Fast Failure**: Quick rejection (100ms timeout) prevents hanging
- **Async Notification**: `RejectedExecutionException` via `CompletableFuture`
- **Rich Metrics**: Queue depth and rejection tracking

**Key Takeaways**:

1. ✅ Always handle `RejectedExecutionException`
2. ✅ Monitor queue depth proactively
3. ✅ Set appropriate `maxQueueSize` based on your workload
4. ✅ Implement retry, circuit breaker, or rate limiting as needed
5. ✅ Test backpressure scenarios
6. ✅ Use metrics to tune configuration

For a complete working example, see `examples/BackpressureExample.java`.

---

**Related Documentation**:
- [README.md](../../README.md) - Main documentation
- [BENCHMARKS.md](BENCHMARKS.md) - Performance benchmarks
- [GRAFANA_DASHBOARD.md](GRAFANA_DASHBOARD.md) - Monitoring setup

