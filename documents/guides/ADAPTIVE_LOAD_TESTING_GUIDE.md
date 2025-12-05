# Adaptive Load Testing with Vortex

**Version**: 0.0.6  
**Date**: 2025-12-05

---

## Overview

This guide explains how to integrate Vortex with adaptive load testing frameworks (e.g., VajraPulse AdaptiveLoadPattern) using queue-only backpressure. This approach provides smooth, automatic TPS (transactions per second) adjustment based on system capacity.

---

## Queue-Only Backpressure Approach

### Why Queue-Only?

**Queue depth directly measures "can the system keep up?"**

- If the queue is full, the system can't process items fast enough (regardless of root cause)
- Simpler than monitoring multiple signals (connection pool, network, CPU, etc.)
- Works with any backend (not just JDBC/databases)
- Provides natural backpressure signal that adapts to actual system capacity

### How It Works

1. **Queue Depth Monitoring**: `QueueDepthBackpressureProvider` monitors the MicroBatcher's internal queue depth
2. **Backpressure Calculation**: `backpressure = queueDepth / maxQueueSize` (0.0 to 1.0)
3. **AdaptiveLoadPattern**: Uses backpressure to adjust TPS gradually (every 5 seconds)
4. **RejectStrategy**: MicroBatcher rejects items immediately when backpressure >= threshold (e.g., 0.7)

---

## Integration with VajraPulse AdaptiveLoadPattern

### Step 1: Create QueueDepthBackpressureProvider

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.backpressure.*;
import java.util.function.Supplier;

// Create queue depth supplier
Supplier<Integer> queueDepthSupplier = () -> batcher.getQueueDepth();

// Create backpressure provider
BackpressureProvider backpressureProvider = new QueueDepthBackpressureProvider(
    queueDepthSupplier,
    maxQueueSize  // e.g., 1000 items (20 batches × 50 items)
);
```

### Step 2: Configure MicroBatcher with Backpressure

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.backpressure.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

// Create backend
Backend<String> backend = batch -> {
    // Your batch processing logic
    return new BatchResult<>(successes, failures);
};

// Create config
BatcherConfig config = BatcherConfig.builder()
    .batchSize(50)
    .lingerTime(Duration.ofMillis(100))
    .maxQueueSize(1000)  // 20 batches worth
    .build();

// Create meter registry
MeterRegistry meterRegistry = new SimpleMeterRegistry();

// Create backpressure strategy (rejects at 70% capacity)
BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);

// Create batcher with backpressure
MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
    backend,
    config,
    meterRegistry,
    backpressureProvider,
    strategy
);
```

### Step 3: Integrate with AdaptiveLoadPattern

```java
import com.vajrapulse.vajrapulse.AdaptiveLoadPattern;
import com.vajrapulse.vajrapulse.MetricsProvider;

// Create metrics provider (from Vortex)
MetricsProvider metricsProvider = batcher.getMetricsProvider();

// Create AdaptiveLoadPattern with queue-only backpressure
AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
    initialTps,           // e.g., 100 TPS
    rampIncrement,        // e.g., 10 TPS
    rampDecrement,        // e.g., 5 TPS
    rampInterval,         // e.g., 5 seconds
    maxTps,               // e.g., 1000 TPS
    sustainDuration,       // e.g., 60 seconds
    errorThreshold,       // e.g., 0.05 (5% error rate)
    metricsProvider,
    backpressureProvider  // Queue-only backpressure
);

// Use pattern in load testing
pattern.start();
```

---

## Recommended Configuration

### Queue Size

**Max Queue Size**: 20-50 batches worth of items

- **Example**: 20 batches × 50 items/batch = 1000 items
- **Larger queue**: More buffering, but more memory
- **Smaller queue**: Less memory, but more rejections

### Backpressure Thresholds

**RejectStrategy Threshold**: 0.7 (70% capacity)

- Rejects items when queue > 70% full
- Prevents queue from filling completely
- Leaves 30% headroom for burst traffic

**AdaptiveLoadPattern Threshold**: 0.7 (70% capacity)

- Ramps down TPS when backpressure >= 0.7
- Should match RejectStrategy threshold for consistency
- Provides smooth adaptation

### Batch Configuration

**Batch Size**: 20-50 items

- Larger batches = higher throughput, higher latency
- Smaller batches = lower latency, lower throughput
- Balance based on your requirements

**Linger Time**: 50-200ms

- Shorter = lower latency, smaller batches
- Longer = larger batches, higher latency
- Balance based on your requirements

---

## Using submitSync() for Load Testing

### Immediate Rejection Feedback

For load testing frameworks that need immediate rejection feedback, use `submitSync()`:

```java
// In load testing task
ItemResult<String> result = batcher.submitSync(item);

if (result instanceof ItemResult.Failure<String>) {
    // Immediate rejection - return failure to framework
    // Framework will see this as a failure and adjust TPS
    return TaskResult.failure(result.getError());
}

// Item accepted - return success
// Use submitWithCallback() to track batch processing results separately
return TaskResult.success();
```

### Hybrid Approach: submitSync() + submitWithCallback()

Track both immediate rejections and batch processing results:

```java
// In load testing task
// Use submitSync() for immediate rejection feedback
ItemResult<String> syncResult = batcher.submitSync(item);

if (syncResult instanceof ItemResult.Failure<String>) {
    // Immediate rejection - return failure to framework
    return TaskResult.failure(syncResult.getError());
}

// Item accepted - use callback for batch processing results
batcher.submitWithCallback(item, (submittedItem, batchResult) -> {
    // Track batch processing results separately
    // (for metrics, not for framework feedback)
    if (batchResult instanceof ItemResult.Success<String>) {
        batchSuccessCounter.increment();
    } else {
        batchFailureCounter.increment();
    }
});

// Return success - item was accepted
return TaskResult.success();
```

---

## Relationship Between Components

### QueueDepthBackpressureProvider vs RejectStrategy

Both use the same backpressure signal, but for different purposes:

| Component | Purpose | Frequency | Action |
|-----------|---------|-----------|--------|
| **QueueDepthBackpressureProvider** | Used by AdaptiveLoadPattern | Every 5 seconds | Gradual TPS adjustment (load pattern level) |
| **RejectStrategy** | Used by MicroBatcher | Every submission | Immediate rejection (item submission level) |

**Why Both?**

- **AdaptiveLoadPattern**: Provides smooth, gradual adaptation (avoids oscillation)
- **RejectStrategy**: Provides immediate protection (prevents queue overflow)

**Both should use the same threshold (0.7) for consistency.**

---

## Best Practices

### 1. Monitor Queue Depth

```java
// Monitor queue depth for metrics/dashboards
int queueDepth = batcher.getQueueDepth();
if (queueDepth > 1000) {
    log.warn("Queue depth is high: {}", queueDepth);
}
```

### 2. Use Consistent Thresholds

- Set RejectStrategy threshold to 0.7
- Set AdaptiveLoadPattern threshold to 0.7
- This ensures consistent behavior

### 3. Size Queue Appropriately

- Queue should be 20-50 batches worth
- Too small = frequent rejections
- Too large = excessive memory usage

### 4. Handle Rejections Gracefully

```java
ItemResult<String> result = batcher.submitSync(item);

if (result instanceof ItemResult.Failure<String> failure) {
    // Handle rejection appropriately:
    // - Retry (with backoff)
    // - Log for analysis
    // - Send to dead letter queue
    // - Fail fast (for load testing)
}
```

### 5. Monitor Metrics

```java
MetricsProvider metrics = batcher.getMetricsProvider();

// Monitor failure rate
double failureRate = metrics.getFailureRate();
if (failureRate > 0.1) {
    log.warn("High failure rate: {}", failureRate);
}

// Monitor queue depth
int queueDepth = metrics.getQueueDepth();
if (queueDepth > maxQueueSize * 0.7) {
    log.warn("Queue approaching capacity: {}/{}", queueDepth, maxQueueSize);
}
```

---

## Troubleshooting

### Queue Filling Up Too Fast

**Symptoms:**
- Frequent rejections
- Queue depth consistently high

**Solutions:**
- Increase `maxQueueSize` (if memory allows)
- Reduce TPS in AdaptiveLoadPattern
- Increase batch size (process more items per batch)
- Optimize backend processing (reduce latency)

### Too Many Rejections

**Symptoms:**
- High rejection rate
- Items rejected even when queue has space

**Solutions:**
- Check race condition: Queue may fill between check and offer
- Increase `maxQueueSize` to provide more headroom
- Lower RejectStrategy threshold (e.g., 0.6 instead of 0.7)
- Monitor for actual queue capacity issues

### Backpressure Not Detected

**Symptoms:**
- Queue fills but backpressure not detected
- AdaptiveLoadPattern doesn't reduce TPS

**Solutions:**
- Verify `QueueDepthBackpressureProvider` is correctly configured
- Check that `getQueueDepth()` returns correct value
- Verify AdaptiveLoadPattern threshold matches RejectStrategy threshold
- Check that backpressure provider is passed to AdaptiveLoadPattern

---

## Example: Complete Integration

```java
import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.backpressure.*;
import com.vajrapulse.vajrapulse.AdaptiveLoadPattern;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.function.Supplier;

public class AdaptiveLoadTestingExample {
    public static void main(String[] args) {
        // 1. Create backend
        Backend<String> backend = batch -> {
            // Process batch
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                try {
                    processItem(item);
                    successes.add(new SuccessEvent<>(item));
                } catch (Exception e) {
                    failures.add(new FailureEvent<>(item, e));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        // 2. Create config
        int batchSize = 50;
        int maxQueueSize = 1000;  // 20 batches
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(batchSize)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(maxQueueSize)
            .build();
        
        // 3. Create meter registry
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // 4. Create batcher
        MicroBatcher<String> batcher = new MicroBatcher<>(
            backend,
            config,
            meterRegistry
        );
        
        // 5. Create backpressure provider
        Supplier<Integer> queueDepthSupplier = () -> batcher.getQueueDepth();
        BackpressureProvider backpressureProvider = new QueueDepthBackpressureProvider(
            queueDepthSupplier,
            maxQueueSize
        );
        
        // 6. Create backpressure strategy
        BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
        
        // 7. Configure batcher with backpressure
        batcher = MicroBatcher.withBackpressure(
            backend,
            config,
            meterRegistry,
            backpressureProvider,
            strategy
        );
        
        // 8. Create metrics provider
        MetricsProvider metricsProvider = batcher.getMetricsProvider();
        
        // 9. Create AdaptiveLoadPattern
        AdaptiveLoadPattern pattern = new AdaptiveLoadPattern(
            100,              // initialTps
            10,               // rampIncrement
            5,                // rampDecrement
            Duration.ofSeconds(5),  // rampInterval
            1000,             // maxTps
            Duration.ofMinutes(1),  // sustainDuration
            0.05,             // errorThreshold (5%)
            metricsProvider,
            backpressureProvider  // Queue-only backpressure
        );
        
        // 10. Use in load testing
        try {
            pattern.start();
            
            // Submit items
            for (int i = 0; i < 10000; i++) {
                ItemResult<String> result = batcher.submitSync("item-" + i);
                
                if (result instanceof ItemResult.Failure<String>) {
                    // Handle rejection
                    System.err.println("Rejected: " + result.getError().getMessage());
                }
            }
        } finally {
            batcher.close();
        }
    }
    
    private static void processItem(String item) {
        // Your processing logic
    }
}
```

---

## Summary

- **Queue-only backpressure** provides a simple, effective signal for adaptive load testing
- **QueueDepthBackpressureProvider** monitors queue depth and calculates backpressure (0.0 to 1.0)
- **RejectStrategy** provides immediate protection by rejecting items when backpressure >= threshold
- **AdaptiveLoadPattern** uses backpressure to gradually adjust TPS
- **Both should use the same threshold (0.7)** for consistency
- **Use `submitSync()`** for immediate rejection feedback in load testing frameworks
- **Monitor queue depth and metrics** to ensure optimal performance

---

**Last Updated**: 2025-12-05

