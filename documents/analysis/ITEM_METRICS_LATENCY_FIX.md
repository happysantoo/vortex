# Item Metrics Latency Fix - 0.0.5

## Problem Analysis

### Current Implementation Issues

1. **`itemSubmitLatency`** - Currently records full latency (submit to completion) ✓ **CORRECT**
2. **`itemWaitTime`** - Currently records full latency (submit to completion) ✗ **INCORRECT**
   - Should only record queue wait time (submit to batch dispatch start)
   - Currently records the same value as `itemSubmitLatency`

### Root Cause

In `ResultProcessor.recordMetrics()`:
- `batchCompletionTime` is captured when results are processed (after backend dispatch completes)
- `waitTime = batchCompletionTime - req.getTimestamp()` calculates full latency
- This full latency is passed to `metrics.recordWaitTime(waitTime)`
- `recordWaitTime()` records the same value to both `itemWaitTime` and `itemSubmitLatency`

### Expected Behavior

- **`itemSubmitLatency`**: Time from `submit()` call to batch completion (includes queue wait + backend processing) ✓
- **`itemWaitTime`**: Time from `submit()` call to batch dispatch start (queue wait only) ✗

## Solution

### Approach 1: Track Queue Wait Time Separately (Recommended)

1. **Add queue wait time tracking in `dispatchBatch()`**:
   - When batch is dispatched, calculate queue wait time for each item
   - Store queue wait time in `PendingRequest` or pass it separately

2. **Modify `MetricsManager.recordWaitTime()`**:
   - Split into two methods:
     - `recordQueueWaitTime(long queueWaitTimeNanos)` - Records queue wait time only
     - `recordItemSubmitLatency(long fullLatencyNanos)` - Records full submit-to-completion latency

3. **Update `ResultProcessor`**:
   - Call `recordQueueWaitTime()` in `dispatchBatch()` when batch starts
   - Call `recordItemSubmitLatency()` in `recordMetrics()` when batch completes

### Approach 2: Calculate Queue Wait Time from Timestamps

1. **Add dispatch timestamp to `PendingRequest`**:
   - Track when batch dispatch starts
   - Calculate queue wait time = dispatchTime - submitTime

2. **Update `recordMetrics()`**:
   - Calculate queue wait time separately
   - Record both metrics correctly

## Implementation Plan

### Phase 1: Add Queue Wait Time Tracking

1. Modify `PendingRequest` to track dispatch time (optional - can calculate from batch dispatch time)
2. In `dispatchBatch()`, calculate queue wait time for each item
3. Add `recordQueueWaitTime()` method to `MetricsManager`
4. Call `recordQueueWaitTime()` in `dispatchBatch()` before backend dispatch

### Phase 2: Fix Full Latency Recording

1. Add `recordItemSubmitLatency()` method to `MetricsManager`
2. Update `ResultProcessor.recordMetrics()` to call `recordItemSubmitLatency()` instead of `recordWaitTime()`
3. Keep `recordWaitTime()` for aggregate metrics (requestWaitLatency, queueWaitTime)

### Phase 3: Testing

1. Add tests to verify:
   - `itemWaitTime` only includes queue wait time
   - `itemSubmitLatency` includes full latency (queue wait + backend processing)
   - Both metrics are recorded when `perItemMetrics` is enabled
   - Metrics are not recorded when `perItemMetrics` is disabled

## Code Changes

### MetricsManager.java

```java
// Add new method for queue wait time
void recordQueueWaitTime(long queueWaitTimeNanos) {
    if (config.isPerItemMetrics() && itemWaitTime != null) {
        itemWaitTime.record(queueWaitTimeNanos, TimeUnit.NANOSECONDS);
    }
}

// Add new method for full submit latency
void recordItemSubmitLatency(long fullLatencyNanos) {
    if (config.isPerItemMetrics() && itemSubmitLatency != null) {
        itemSubmitLatency.record(fullLatencyNanos, TimeUnit.NANOSECONDS);
    }
}

// Keep recordWaitTime for aggregate metrics
void recordWaitTime(long waitTimeNanos) {
    requestWaitLatency.record(waitTimeNanos, TimeUnit.NANOSECONDS);
    queueWaitTime.record(waitTimeNanos, TimeUnit.NANOSECONDS);
    // Remove per-item recording from here
}
```

### MicroBatcher.java

```java
private void dispatchBatch(List<PendingRequest<T>> batch) {
    // ... existing code ...
    
    long dispatchStartTime = System.nanoTime();
    
    // Record queue wait time for each item
    if (config.isPerItemMetrics()) {
        for (PendingRequest<T> req : batch) {
            long queueWaitTime = dispatchStartTime - req.getTimestamp();
            metrics.recordQueueWaitTime(queueWaitTime);
        }
    }
    
    // ... rest of dispatch logic ...
}
```

### ResultProcessor.java

```java
private void recordMetrics(PendingRequest<T> req, long batchCompletionTime) {
    long fullLatency = batchCompletionTime - req.getTimestamp();
    metrics.recordWaitTime(fullLatency); // For aggregate metrics
    
    // Record per-item full latency
    if (config.isPerItemMetrics()) {
        metrics.recordItemSubmitLatency(fullLatency);
    }
}
```

## Testing Strategy

1. **Unit Test**: Verify queue wait time is recorded correctly
2. **Integration Test**: Verify full latency includes backend processing time
3. **Performance Test**: Ensure no performance regression

## Impact

- **Breaking Changes**: None (metric names remain the same)
- **Performance**: Minimal overhead (one additional timestamp calculation per item)
- **Backward Compatibility**: Full (existing metrics continue to work)

