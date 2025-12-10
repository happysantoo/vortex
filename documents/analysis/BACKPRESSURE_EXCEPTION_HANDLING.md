# Backpressure Exception Handling Guide

**Date**: December 6, 2025  
**Version**: 0.0.8  
**Purpose**: Guide for applications on handling exceptions when items are rejected due to capacity constraints

## Overview

When using Vortex Micro-Batching Library, applications need to handle rejections that can occur during item submission. **All rejections are unified** into a single exception type: `BackpressureException` (which extends `RejectedExecutionException`).

**Key Principle**: From the application's perspective, all rejections mean "can't accept item right now" and should be handled the same way (e.g., store to overflow, retry with backoff).

---

## Unified Exception: BackpressureException

**Exception Type**: `BackpressureException` extends `RejectedExecutionException`

**All rejection scenarios use this exception**:
1. Backpressure detected (queue depth > threshold)
2. Queue full (queue at capacity)
3. Concurrent batch limit reached

**Why Unified?**
- Applications handle all rejections the same way
- Single exception type simplifies error handling
- Rich metadata available for monitoring/debugging
- Maintains compatibility with standard Java `RejectedExecutionException`

---

## Exception Scenarios

### 1. Backpressure Detected (RejectStrategy)

**When it occurs**: When backpressure level exceeds the configured threshold (e.g., queue depth > 70% of capacity).

**Exception Type**: `BackpressureException` (extends `RejectedExecutionException`)

**How it's delivered**:
- **Async (`submit()` method)**: Exception is wrapped in `CompletableFuture` and delivered via `future.completeExceptionally()`
- **Sync (`submitSync()` method)**: Exception is wrapped in `ItemResult.failure()` with the exception as the cause
- **Callback (`submitWithCallback()` method)**: Exception is delivered via callback as `ItemResult.failure()`

**Exception Details**:
```java
public class BackpressureException extends RuntimeException {
    private final double backpressureLevel;  // Current backpressure (0.0 to 1.0)
    private final double threshold;         // Threshold that was exceeded
    private final String sourceName;         // Source of backpressure (e.g., "Vortex Queue Depth")
}
```

**Example Exception Message**:
```
"Backpressure too high: 0.85 (threshold: 0.70, source: Vortex Queue Depth)"
```

**Code Reference**:
```java
// From RejectStrategy.java:45-55
Exception reason = new BackpressureException(
    String.format(
        "Backpressure too high: %.2f (threshold: %.2f, source: %s)",
        context.backpressureLevel(),
        threshold,
        context.provider().getSourceName()
    ),
    context.backpressureLevel(),
    threshold,
    context.provider().getSourceName()
);
```

---

### 2. Queue Full

**When it occurs**: When the internal queue is at capacity and cannot accept more items. This happens **after** backpressure check passes (or if backpressure is not configured).

**Exception Type**: `BackpressureException` (extends `RejectedExecutionException`)

**How it's delivered**:
- **Async (`submit()` method)**: Exception is wrapped in `CompletableFuture` and delivered via `future.completeExceptionally()`
- **Sync (`submitSync()` method)**: Exception is wrapped in `ItemResult.failure()` with the exception as the cause
- **Callback (`submitWithCallback()` method)**: Exception is delivered via callback as `ItemResult.failure()`

**Exception Details**:
- `backpressureLevel`: `1.0` (100% - queue is full)
- `threshold`: `1.0` (100% threshold)
- `sourceName`: `"Vortex Queue Depth"`

**Exception Message**: `"Queue full: X/Y"` (where X is current queue size, Y is max queue size)

**Code Reference**:
```java
// From MicroBatcher.java:460-463 (async submit)
if (!queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
    metrics.recordRequestRejected();
    future.completeExceptionally(BackpressureException.queueFull(currentSize, maxSize));
    return future;
}

// From MicroBatcher.java:637-639 (sync submitSync)
return ItemResult.failure(item, BackpressureException.queueFull(currentQueueSize, maxQueueSize));
```

---

### 3. Concurrent Batch Limit Reached

**When it occurs**: When `maxConcurrentBatches` is configured and the limit is reached. New batches cannot be dispatched until an in-flight batch completes.

**Exception Type**: `BackpressureException` (extends `RejectedExecutionException`)

**Exception Details**:
- `backpressureLevel`: `activeBatches / maxBatches` (e.g., 8/8 = 1.0)
- `threshold`: `1.0` (100% threshold)
- `sourceName`: `"Concurrent Batches"`

**Exception Message**: `"Batch rejected: too many concurrent batches (active: X, limit: Y)"`

**Code Reference**:
```java
// From MicroBatcher.java:1033-1035
BackpressureException rejectionError = BackpressureException.concurrentLimitReached(
    activeBatches, maxConcurrentBatches);
```

**Note**: This exception affects entire batches, not individual items. All items in the rejected batch will receive this exception.

---

### 4. Batcher Closed

**When it occurs**: When attempting to submit items after `close()` has been called.

**Exception Type**: `IllegalStateException` (extends `RuntimeException`)

**Exception Message**:
```
"MicroBatcher is closed. Queue depth: X, Active batches: Y"
```

**Code Reference**:
```java
// From MicroBatcher.java:380-383
if (closed) {
    throw new IllegalStateException(
        String.format("MicroBatcher is closed. Queue depth: %d, Active batches: %d",
            queue.size(), activeBatchCount != null ? activeBatchCount.get() : 0)
    );
}
```

---

### 5. InterruptedException

**When it occurs**: When the thread is interrupted while waiting to offer an item to the queue (async `submit()` only).

**Exception Type**: `InterruptedException` (checked exception)

**How it's delivered**: Wrapped in `CompletableFuture` via `future.completeExceptionally(e)`

**Code Reference**:
```java
// From MicroBatcher.java:465-468
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    future.completeExceptionally(e);
}
```

---

## Exception Handling Patterns

### Pattern 1: Async Submission with CompletableFuture (Unified)

```java
CompletableFuture<BatchResult<String>> future = batcher.submit(item);

future.whenComplete((result, error) -> {
    if (error != null) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        
        if (cause instanceof BackpressureException) {
            // All rejections: backpressure, queue full, or concurrent limit
            BackpressureException bpEx = (BackpressureException) cause;
            handleRejection(item, bpEx);
        } else if (cause instanceof RejectedExecutionException) {
            // Fallback for any other RejectedExecutionException (shouldn't happen, but safe)
            handleRejection(item, (RejectedExecutionException) cause);
        } else if (error instanceof IllegalStateException) {
            // Batcher is closed
            handleBatcherClosed(item, (IllegalStateException) error);
        } else if (cause instanceof InterruptedException) {
            // Thread interrupted
            handleInterruption(item, (InterruptedException) cause);
        } else {
            // Unexpected error
            handleUnexpectedError(item, error);
        }
    } else {
        // Success
        handleSuccess(result);
    }
});
```

### Pattern 2: Sync Submission with ItemResult (Unified)

```java
ItemResult<String> result = batcher.submitSync(item);

if (result.isSuccess()) {
    handleSuccess(result);
} else {
    Throwable error = result.error();
    
    if (error instanceof BackpressureException) {
        // All rejections: backpressure, queue full, or concurrent limit
        BackpressureException bpEx = (BackpressureException) error;
        handleRejection(item, bpEx);
    } else if (error instanceof RejectedExecutionException) {
        // Fallback (shouldn't happen, but safe)
        handleRejection(item, (RejectedExecutionException) error);
    } else if (error instanceof IllegalStateException) {
        // Batcher is closed
        handleBatcherClosed(item, (IllegalStateException) error);
    } else {
        // Unexpected error
        handleUnexpectedError(item, error);
    }
}
```

### Pattern 3: Callback-Based Submission (Unified)

```java
batcher.submitWithCallback(item, (submittedItem, itemResult) -> {
    if (itemResult.isSuccess()) {
        handleSuccess(itemResult);
    } else {
        Throwable error = itemResult.error();
        
        if (error instanceof BackpressureException) {
            // All rejections: backpressure, queue full, or concurrent limit
            BackpressureException bpEx = (BackpressureException) error;
            handleRejection(submittedItem, bpEx);
        } else if (error instanceof RejectedExecutionException) {
            // Fallback (shouldn't happen, but safe)
            handleRejection(submittedItem, (RejectedExecutionException) error);
        } else if (error instanceof IllegalStateException) {
            // Batcher is closed
            handleBatcherClosed(submittedItem, (IllegalStateException) error);
        } else {
            // Unexpected error
            handleUnexpectedError(submittedItem, error);
        }
    }
});
```

---

## Recommended Application Handling

### Unified Rejection Handling

**All rejections (backpressure, queue full, concurrent limit) should be handled the same way.**

```java
private void handleRejection(String item, BackpressureException ex) {
    // Log with rich metadata for monitoring
    logger.warn("Item rejected: level={:.2f}, threshold={:.2f}, source={}, message={}",
        ex.getBackpressureLevel(), ex.getThreshold(), ex.getSourceName(), ex.getMessage());
    
    // Store to application-managed overflow
    if (overflowQueue.offer(item)) {
        overflowCount.incrementAndGet();
        logger.debug("Item stored to overflow (size: {})", overflowQueue.size());
    } else {
        // Overflow is full - application decides: log, alert, DLQ, drop, etc.
        logger.error("Overflow storage full, dropping item: {}", item);
        // Optionally: sendToDeadLetterQueue(item);
    }
}
```

**Why Unified?**
- All rejections mean "can't accept item right now"
- Same handling logic (store to overflow, retry, etc.)
- Rich metadata available for monitoring/debugging
- Simpler application code

### 4. IllegalStateException (Batcher Closed) Handling

**Recommended Action**: Log and handle gracefully (batcher is shutting down)

```java
private void handleBatcherClosed(String item, IllegalStateException ex) {
    logger.error("Cannot submit item - batcher is closed: {}", ex.getMessage());
    
    // Option 1: Store to persistent storage for later processing
    // Option 2: Send to dead letter queue
    // Option 3: Log and drop (if acceptable)
    sendToDeadLetterQueue(item, "Batcher closed");
}
```

### 5. InterruptedException Handling

**Recommended Action**: Restore interrupt flag and handle gracefully

```java
private void handleInterruption(String item, InterruptedException ex) {
    logger.warn("Submission interrupted: {}", item);
    Thread.currentThread().interrupt(); // Restore interrupt flag
    
    // Option 1: Retry if appropriate
    // Option 2: Store to overflow
    // Option 3: Handle according to application shutdown policy
    if (overflowQueue.offer(item)) {
        overflowCount.incrementAndGet();
    }
}
```

---

## Exception Hierarchy

```
RuntimeException
├── RejectedExecutionException (Java standard)
│   └── BackpressureException  (All rejections: backpressure, queue full, concurrent limit)
├── IllegalStateException      (Batcher closed)
└── InterruptedException       (Thread interrupted - checked exception)
```

---

## Unified Exception Handling

**All rejection scenarios now use `BackpressureException`**:

| Scenario | Exception Type | backpressureLevel | threshold | sourceName |
|----------|---------------|-------------------|-----------|------------|
| **Backpressure detected** | `BackpressureException` | Actual level (e.g., 0.85) | Configured threshold (e.g., 0.7) | Provider name (e.g., "Vortex Queue Depth") |
| **Queue full** | `BackpressureException` | `1.0` (100%) | `1.0` (100%) | `"Vortex Queue Depth"` |
| **Concurrent limit** | `BackpressureException` | `activeBatches / maxBatches` | `1.0` (100%) | `"Concurrent Batches"` |

**Application Action**: All rejections should be handled the same way (store to overflow, retry with backoff, etc.)

---

## Complete Example: Exception Handling

```java
public class ApplicationWithOverflowHandling {
    private final MicroBatcher<String> batcher;
    private final Queue<String> overflowQueue = new LinkedBlockingQueue<>(10000);
    
    public void processItem(String item) {
        try {
            CompletableFuture<BatchResult<String>> future = batcher.submit(item);
            
            future.whenComplete((result, error) -> {
                if (error != null) {
                    handleSubmissionError(item, error);
                } else {
                    // Success
                    logger.debug("Item processed successfully");
                }
            });
        } catch (IllegalStateException e) {
            // Batcher is closed (synchronous exception)
            handleBatcherClosed(item, e);
        }
    }
    
    private void handleSubmissionError(String item, Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        
        if (cause instanceof BackpressureException) {
            BackpressureException bpEx = (BackpressureException) cause;
            logger.warn("Backpressure: level={:.2f}, threshold={:.2f}, source={}",
                bpEx.getBackpressureLevel(), bpEx.getThreshold(), bpEx.getSourceName());
            storeToOverflow(item);
            
        } else if (cause instanceof RejectedExecutionException) {
            RejectedExecutionException rejEx = (RejectedExecutionException) cause;
            String message = rejEx.getMessage();
            
            if (message != null && message.contains("Queue")) {
                logger.warn("Queue full: {}", message);
                storeToOverflow(item);
            } else if (message != null && message.contains("concurrent batches")) {
                logger.warn("Concurrent batch limit: {}", message);
                // Retry or store to overflow
                scheduleRetryWithBackoff(item);
            } else {
                logger.warn("Rejected: {}", message);
                storeToOverflow(item);
            }
            
        } else if (cause instanceof InterruptedException) {
            logger.warn("Submission interrupted");
            Thread.currentThread().interrupt();
            storeToOverflow(item);
            
        } else {
            logger.error("Unexpected error during submission", error);
            // Handle according to application policy
        }
    }
    
    private void storeToOverflow(String item) {
        if (overflowQueue.offer(item)) {
            logger.debug("Stored to overflow (size: {})", overflowQueue.size());
        } else {
            logger.error("Overflow storage full, dropping item: {}", item);
            // Application policy: DLQ, alert, etc.
        }
    }
    
    private void handleBatcherClosed(String item, IllegalStateException e) {
        logger.error("Batcher closed, cannot submit: {}", e.getMessage());
        // Store to persistent storage or DLQ
    }
    
    private void scheduleRetryWithBackoff(String item) {
        // Implement retry logic with exponential backoff
    }
}
```

---

## Summary

Applications should handle these exceptions:

1. **`BackpressureException`** (extends `RejectedExecutionException`): **All rejections**
   - Backpressure detected (queue depth > threshold)
   - Queue full (queue at capacity)
   - Concurrent batch limit reached
   - **Action**: Store to overflow, retry with backoff, etc.

2. **`IllegalStateException`**: Batcher is closed
   - **Action**: Persistent storage or DLQ

3. **`InterruptedException`**: Thread interrupted
   - **Action**: Restore interrupt flag, handle gracefully

**Key Principle**: 
- **Unified Exception**: All rejections use `BackpressureException` (which extends `RejectedExecutionException`)
- **Same Handling**: All rejections should be handled the same way (store to overflow, retry, etc.)
- **Rich Metadata**: `BackpressureException` provides level, threshold, and source for monitoring
- **Application Responsibility**: The library **signals** rejections. The application **handles** overflow storage and replay logic.

---

**Analysis Complete**

