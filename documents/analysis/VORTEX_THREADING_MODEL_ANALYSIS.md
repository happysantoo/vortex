# Vortex Threading Model Analysis

**Date**: December 23, 2025  
**Project**: Vortex Micro-Batching Library  
**Version**: 0.0.10

---

## Executive Summary

Yes, Vortex spawns its micro-batching operations on separate threads. The library uses **Java 21 Virtual Threads** as its primary threading model for high-throughput, low-overhead concurrency. This document provides a detailed analysis of the threading model, thread handover mechanisms, and a plan to add the "vortex-" prefix to all thread names for better observability.

---

## Table of Contents

1. [Current Threading Architecture](#current-threading-architecture)
2. [Thread Types and Purposes](#thread-types-and-purposes)
3. [Thread Handover Flow](#thread-handover-flow)
4. [Current Thread Naming](#current-thread-naming)
5. [Problem Statement](#problem-statement)
6. [Plan of Action](#plan-of-action)
7. [Implementation Details](#implementation-details)
8. [Testing Strategy](#testing-strategy)

---

## Current Threading Architecture

### Overview

Vortex uses a hybrid threading model:

| Thread Type | Purpose | Count | Named? |
|-------------|---------|-------|--------|
| Virtual Threads (per-task) | Batch processing, dispatch, retries | Many (pooled) | ❌ No |
| Platform Thread (scheduled) | Retry cleanup | 1 per batcher | ✅ Yes (`vortex-retry-cleanup`) |

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           USER APPLICATION                                   │
│                                                                              │
│  Thread: "main" or "http-handler-*" etc.                                    │
│  ┌──────────────────┐                                                       │
│  │ batcher.submit() │──────────────────────────────────┐                    │
│  └──────────────────┘                                  │                    │
└────────────────────────────────────────────────────────│────────────────────┘
                                                         │
                                                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           VORTEX LIBRARY                                     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    LinkedBlockingQueue<PendingRequest>               │    │
│  │                         (bounded, configurable size)                 │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │              VIRTUAL THREAD: Batch Processor Loop                    │    │
│  │                    (unnamed - should be "vortex-batch-processor")    │    │
│  │                                                                      │    │
│  │   while (!closed || !queue.isEmpty()) {                             │    │
│  │       batch = formBatch();  // polls queue with timeout             │    │
│  │       dispatchBatch(batch);                                         │    │
│  │   }                                                                 │    │
│  └──────────────────────────────┬──────────────────────────────────────┘    │
│                                 │                                            │
│                                 ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │            VIRTUAL THREADS: Batch Dispatch Workers                   │    │
│  │                 (unnamed - should be "vortex-dispatch-*")            │    │
│  │                                                                      │    │
│  │   executor.submit(() -> {                                           │    │
│  │       result = backend.dispatch(dataList);  // May block (I/O)      │    │
│  │       processResults(batch, result);                                │    │
│  │   });                                                               │    │
│  └──────────────────────────────┬──────────────────────────────────────┘    │
│                                 │                                            │
│                                 ▼                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │            VIRTUAL THREADS: Retry Workers (if retries enabled)       │    │
│  │                 (unnamed - should be "vortex-retry-*")               │    │
│  │                                                                      │    │
│  │   executor.submit(() -> {                                           │    │
│  │       Thread.sleep(retryDelay);                                     │    │
│  │       submitFunction.apply(item);                                   │    │
│  │   });                                                               │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │          PLATFORM THREAD: Retry Cleanup (Scheduled)                  │    │
│  │                    ✅ Named: "vortex-retry-cleanup"                  │    │
│  │                                                                      │    │
│  │   Runs every 5 minutes to clean up stale retry entries              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Thread Types and Purposes

### 1. Virtual Thread Executor (`Executors.newVirtualThreadPerTaskExecutor()`)

**Location**: `MicroBatcher.java:121`

```java
this.executor = Executors.newVirtualThreadPerTaskExecutor();
```

**Purpose**: Primary executor for all async operations

**Characteristics**:
- Creates a new virtual thread per task
- Virtual threads are lightweight (~1KB vs ~1MB for platform threads)
- Automatically scales to millions of concurrent tasks
- Ideal for I/O-bound operations (database calls, HTTP requests)

**Used By**:
| Component | Method | Purpose |
|-----------|--------|---------|
| `MicroBatcher` | `startBatchProcessor()` | Main batch processing loop |
| `BatchDispatcher` | `dispatchBatch()` | Execute backend dispatch |
| `RetryManager` | `scheduleRetry()` | Submit retry tasks |

### 2. Scheduled Platform Thread (`ScheduledExecutorService`)

**Location**: `RetryManager.java:71-75`

```java
this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "vortex-retry-cleanup");
    t.setDaemon(true);
    return t;
});
```

**Purpose**: Periodic cleanup of stale retry entries

**Characteristics**:
- Single platform thread (heavier than virtual threads)
- Runs every 5 minutes
- Set as daemon thread (won't prevent JVM shutdown)
- **Already named**: `"vortex-retry-cleanup"`

---

## Thread Handover Flow

### Complete Flow Diagram

```
User Thread                    Vortex Threads
    │
    │ 1. submit(item)
    │ ─────────────────────►  [Queue.offer()]
    │                              │
    │ ◄─────────────────────  2. Return ItemResult.SUCCESS
    │   (immediate return)         │
    │                              │
    │                         3. Batch Processor Thread (virtual)
    │                              │ polls queue
    │                              │ forms batch
    │                              ▼
    │                         4. Dispatch Thread (virtual)
    │                              │ calls backend.dispatch()
    │                              │ (may block on I/O)
    │                              ▼
    │                         5. Complete futures/callbacks
    │ ◄─────────────────────       │
    │   callback.onResult()        │
    │   (on dispatch thread)       │
    │                              │
    │                         6. If failure + retryable:
    │                              │ Retry Thread (virtual)
    │                              │ sleep(retryDelay)
    │                              │ resubmit to queue
    │                              ▼
    │                         7. Back to step 3
```

### Detailed Handover Points

#### Handover 1: User Thread → Queue
```java
// SubmissionHandler.java
public SubmissionContext<T> submitCommon(T item, boolean applyThreshold, boolean useTimeout) {
    // ... validation ...
    
    // HANDOVER: User thread adds to queue, returns immediately
    boolean offered = queue.offer(request);  // Non-blocking for sync submit
    // OR
    boolean offered = queue.offer(request, TIMEOUT, TimeUnit.MILLISECONDS);  // Timed for retries
    
    return new SubmissionContext<>(future, enqueueResult);
}
```

#### Handover 2: Queue → Batch Processor Thread
```java
// MicroBatcher.java
private void startBatchProcessor() {
    executor.submit(() -> {
        // This runs on a virtual thread
        while (!closed || !queue.isEmpty()) {
            processBatch();  // Polls from queue
        }
    });
}

// BatchFormationStrategy.java
public List<PendingRequest<T>> formBatch() throws InterruptedException {
    // HANDOVER: Batch processor thread takes items from queue
    PendingRequest<T> first = queue.poll(lingerTime.toMillis(), TimeUnit.MILLISECONDS);
    // ... collect more items up to batchSize or timeout ...
}
```

#### Handover 3: Batch Processor → Dispatch Thread
```java
// BatchDispatcher.java
public void dispatchBatch(List<PendingRequest<T>> batch) {
    // HANDOVER: Create new virtual thread for dispatch
    executor.submit(() -> {
        // This runs on a NEW virtual thread
        BatchResult<T> result = backend.dispatch(dataList);  // May block
        resultProcessor.processResults(batch, result);
    });
}
```

#### Handover 4: Dispatch Thread → User Callback
```java
// MicroBatcher.java
if (callback != null) {
    context.batchFuture.thenAccept(batchResult -> {
        // HANDOVER: Callback runs on the dispatch thread
        ItemResult<T> itemResult = batchResult.findItemResult(item).orElseThrow();
        callback.onResult(itemResult);  // User code executes here
    });
}
```

#### Handover 5: Failure → Retry Thread
```java
// RetryManager.java
void scheduleRetry(T item, Throwable error, CompletableFuture<BatchResult<T>> originalFuture) {
    executor.submit(() -> {
        // HANDOVER: New virtual thread for retry
        Thread.sleep(config.getRetryDelay().toMillis());
        CompletableFuture<BatchResult<T>> retryFuture = submitFunction.apply(item);
        // Links retry result back to original future
    });
}
```

---

## Current Thread Naming

| Thread | Named? | Current Name | Desired Name |
|--------|--------|--------------|--------------|
| Batch Processor | ❌ No | (unnamed virtual thread) | `vortex-batch-processor` |
| Dispatch Workers | ❌ No | (unnamed virtual threads) | `vortex-dispatch-N` |
| Retry Workers | ❌ No | (unnamed virtual threads) | `vortex-retry-N` |
| Retry Cleanup | ✅ Yes | `vortex-retry-cleanup` | `vortex-retry-cleanup` (keep) |

### Current Thread Dump Example (Unnamed)

```
"" #42 virtual
   java.base/java.lang.VirtualThread.run(VirtualThread.java:309)
   java.base/java.lang.VirtualThread$VThreadContinuation$1.run(VirtualThread.java:192)

"" #43 virtual
   java.base/java.lang.VirtualThread.run(VirtualThread.java:309)
   ...
```

### Desired Thread Dump Example (Named)

```
"vortex-batch-processor" #42 virtual
   java.base/java.lang.VirtualThread.run(VirtualThread.java:309)
   ...

"vortex-dispatch-1" #43 virtual
   java.base/java.lang.VirtualThread.run(VirtualThread.java:309)
   ...
```

---

## Problem Statement

### Issues with Unnamed Threads

1. **Debugging Difficulty**: Thread dumps show empty names, making it hard to identify Vortex threads
2. **Monitoring Gaps**: APM tools can't distinguish library threads from application threads
3. **Inconsistency**: Only `vortex-retry-cleanup` is named, others are not
4. **Production Troubleshooting**: When issues occur, unnamed threads obscure the source

---

## Plan of Action

### Phase 1: Create Named Virtual Thread Factory

**Approach**: Use Java 21's `Thread.ofVirtual().name(prefix, startIndex)` to create named virtual threads.

```java
// Create a ThreadFactory that produces named virtual threads
ThreadFactory vortexThreadFactory = Thread.ofVirtual()
    .name("vortex-worker-", 0)  // Names: vortex-worker-0, vortex-worker-1, ...
    .factory();

// Use with ExecutorService
ExecutorService executor = Executors.newThreadPerTaskExecutor(vortexThreadFactory);
```

### Phase 2: Implement Custom Thread Factories

Create distinct thread pools for different purposes:

| Purpose | Thread Name Pattern | Count |
|---------|---------------------|-------|
| Batch Processing | `vortex-batch-processor` | 1 per batcher |
| Batch Dispatch | `vortex-dispatch-N` | Per-task |
| Retry Workers | `vortex-retry-N` | Per-task |

### Phase 3: Update Components

| File | Change |
|------|--------|
| `MicroBatcher.java` | Use named thread factory for executor |
| `BatchDispatcher.java` | No change (uses MicroBatcher's executor) |
| `RetryManager.java` | Keep existing cleanup thread naming |

### Phase 4: Testing

- Verify thread names appear in thread dumps
- Verify all tests still pass
- Verify no performance regression

---

## Implementation Details

### Option A: Single Named Thread Pool (Simpler)

```java
// MicroBatcher.java
private ExecutorService createNamedVirtualThreadExecutor() {
    ThreadFactory factory = Thread.ofVirtual()
        .name("vortex-", 0)
        .factory();
    return Executors.newThreadPerTaskExecutor(factory);
}
```

**Pros**: Simple, single prefix for all Vortex threads  
**Cons**: Can't distinguish batch processor from dispatch workers

### Option B: Multiple Named Thread Pools (More Granular) - RECOMMENDED

```java
// VortexThreadFactory.java (new utility class)
public final class VortexThreadFactory {
    private static final AtomicLong BATCHER_COUNTER = new AtomicLong(0);
    
    private VortexThreadFactory() {} // Utility class
    
    /**
     * Creates a ThreadFactory for named virtual threads.
     * @param prefix the thread name prefix (e.g., "vortex-dispatch-")
     * @return a ThreadFactory producing named virtual threads
     */
    public static ThreadFactory virtualThreadFactory(String prefix) {
        return Thread.ofVirtual()
            .name(prefix, 0)
            .factory();
    }
    
    /**
     * Creates an ExecutorService using named virtual threads.
     */
    public static ExecutorService newVirtualThreadExecutor(String prefix) {
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory(prefix));
    }
}
```

```java
// MicroBatcher.java - Updated constructor
public MicroBatcher(Backend<T> backend, BatcherConfig config, MeterRegistry meterRegistry) {
    // ... validation ...
    
    // Use named virtual threads for executor
    long batcherId = BATCHER_COUNTER.incrementAndGet();
    this.executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual()
            .name("vortex-" + batcherId + "-", 0)
            .factory()
    );
    
    // ... rest of constructor ...
}
```

**Thread naming pattern**: `vortex-{batcherId}-{threadNum}`
- Example: `vortex-1-0`, `vortex-1-1`, `vortex-2-0`, etc.

### Files to Modify

| File | Changes |
|------|---------|
| `MicroBatcher.java` | Replace `Executors.newVirtualThreadPerTaskExecutor()` with named version |
| (Optional) `VortexThreadFactory.java` | New utility class for thread factory creation |

### Implementation Code

```java
// MicroBatcher.java - Line 120-121 (current)
// Use virtual threads for executor
this.executor = Executors.newVirtualThreadPerTaskExecutor();

// MicroBatcher.java - Line 120-125 (proposed)
// Use named virtual threads for executor
// Each MicroBatcher instance gets a unique ID for thread naming
private static final AtomicLong INSTANCE_COUNTER = new AtomicLong(0);

// In constructor:
long instanceId = INSTANCE_COUNTER.incrementAndGet();
this.executor = Executors.newThreadPerTaskExecutor(
    Thread.ofVirtual()
        .name("vortex-" + instanceId + "-worker-", 0)
        .factory()
);
```

---

## Testing Strategy

### Unit Tests

1. **Thread Name Verification**:
```groovy
def "should create threads with vortex prefix"() {
    given:
        def threadNames = Collections.synchronizedSet(new HashSet<String>())
        def backend = { batch ->
            threadNames.add(Thread.currentThread().getName())
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def batcher = new MicroBatcher<>(backend, config)
    
    when:
        batcher.submit("item")
        Thread.sleep(200)
    
    then:
        threadNames.every { it.startsWith("vortex-") }
    
    cleanup:
        batcher?.close()
}
```

2. **Existing Tests**: All current tests should continue to pass

### Manual Verification

```bash
# Take thread dump while running tests
jcmd <pid> Thread.print | grep "vortex-"

# Expected output:
# "vortex-1-worker-0" #42 virtual
# "vortex-1-worker-1" #43 virtual
# "vortex-retry-cleanup" #44
```

---

## Summary

| Aspect | Current State | After Implementation |
|--------|---------------|---------------------|
| Batch Processor | Unnamed virtual thread | `vortex-{id}-worker-0` |
| Dispatch Workers | Unnamed virtual threads | `vortex-{id}-worker-N` |
| Retry Workers | Unnamed virtual threads | `vortex-{id}-worker-N` |
| Retry Cleanup | `vortex-retry-cleanup` | `vortex-retry-cleanup` (unchanged) |
| Thread Identification | Difficult | Easy - all start with `vortex-` |
| Debugging | Hard | Easy - clear naming in dumps |
| APM Integration | Poor | Good - threads identifiable |

---

## Appendix: Java 21 Virtual Thread Naming API

```java
// Thread.Builder.OfVirtual - Java 21+
Thread.ofVirtual()
    .name("prefix-", startIndex)  // Creates: prefix-0, prefix-1, etc.
    .inheritInheritableThreadLocals(false)  // Optimization for virtual threads
    .factory();  // Returns ThreadFactory

// Using with ExecutorService
ExecutorService executor = Executors.newThreadPerTaskExecutor(factory);
```

### Why Virtual Threads?

1. **Scalability**: Can have millions of concurrent virtual threads
2. **Efficiency**: Lightweight (~1KB stack vs ~1MB for platform threads)
3. **Simplicity**: Write blocking code that scales like async
4. **I/O Optimization**: Automatically unmount from carrier thread during I/O

---

## Next Steps

1. ✅ Analysis complete (this document)
2. ✅ Implement named thread factory in MicroBatcher (v0.0.12)
3. ✅ Add thread naming test (`MicroBatcherThreadNamingSpec.groovy`)
4. ✅ Verify all tests pass
5. ✅ Update documentation (CHANGELOG.md)

---

## Implementation Status

**Status**: ✅ COMPLETED in version 0.0.12

**Implementation Date**: December 26, 2025

### Changes Made

| File | Change |
|------|--------|
| `MicroBatcher.java` | Added `INSTANCE_COUNTER`, `instanceId`, `dispatchExecutor`, `retryExecutor`. Updated `startBatchProcessor()` to use named virtual thread. |
| `ShutdownManager.java` | Updated to accept and shutdown both `dispatchExecutor` and `retryExecutor`. |
| `MicroBatcherThreadNamingSpec.groovy` | New test file verifying thread naming conventions. |
| `CHANGELOG.md` | Added 0.0.12 release notes. |
| `build.gradle.kts` | Updated version to 0.0.12. |

### Thread Naming Result

```
"vortex-1-batch-processor" #42 virtual
"vortex-1-dispatch-0" #43 virtual
"vortex-1-dispatch-1" #44 virtual
"vortex-1-retry-0" #45 virtual
"vortex-retry-cleanup" #46
```

