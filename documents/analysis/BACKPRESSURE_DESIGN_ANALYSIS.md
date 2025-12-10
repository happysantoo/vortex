# Backpressure Package Design Analysis

**Date**: December 6, 2025  
**Version**: 0.0.7  
**Focus**: Design review of backpressure package, specifically questioning whether OverflowStrategy belongs in the library

## Executive Summary

The backpressure package provides a flexible, extensible design for handling system overload. However, the **OverflowStrategy** introduces application-level concerns (storage, replay, lifecycle management) that may be better handled by the consuming application. This analysis examines the design's strengths, gaps, and provides recommendations.

---

## Current Architecture

### Core Components

1. **BackpressureProvider** (Interface)
   - Detects backpressure level (0.0 to 1.0)
   - Examples: QueueDepthBackpressureProvider, CompositeBackpressureProvider
   - **Role**: Signal detection

2. **BackpressureStrategy** (Interface)
   - Handles items when backpressure detected
   - Actions: ACCEPT, REJECT, DROP
   - **Role**: Decision making

3. **Built-in Strategies**
   - `RejectStrategy`: Rejects with exception
   - `DropStrategy`: Silently drops items
   - `OverflowStrategy`: Stores to overflow and replays later

4. **OverflowStrategy Components**
   - `OverflowStorage<T>`: Interface for temporary storage
   - `InMemoryOverflowStorage<T>`: In-memory implementation
   - `LifecycleAwareStrategy`: Callbacks for state transitions
   - Replay mechanism with pause/resume callbacks

---

## Advantages of Current Design

### ✅ 1. Separation of Concerns (Partial)

**Provider vs Strategy Separation**
- Clear separation between **detection** (Provider) and **handling** (Strategy)
- Allows different detection sources (queue depth, connection pool, CPU) with same strategies
- Enables composition via `CompositeBackpressureProvider`

**Example:**
```java
// Same strategy works with different providers
BackpressureProvider queueProvider = new QueueDepthBackpressureProvider(...);
BackpressureProvider poolProvider = new ConnectionPoolBackpressureProvider(...);
BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
```

### ✅ 2. Extensibility

**Strategy Pattern**
- Applications can implement custom strategies
- Library provides sensible defaults (Reject, Drop)
- Easy to add new strategies without modifying core library

**Example:**
```java
// Custom strategy for application-specific needs
public class CircuitBreakerStrategy<T> implements BackpressureStrategy<T> {
    // Application-specific logic
}
```

### ✅ 3. Flexible Integration

**LifecycleAwareStrategy**
- Allows strategies to coordinate with external systems
- Useful for Kafka consumer pause/resume
- Enables stateful strategies

### ✅ 4. Clear API

**Simple Interface**
- `BackpressureProvider.getBackpressureLevel()` - simple, fast
- `BackpressureStrategy.handle(context)` - clear contract
- Minimal dependencies

---

## Gaps and Concerns

### ❌ 1. OverflowStrategy Belongs to Application Domain

**Problem**: OverflowStrategy introduces application-level concerns that don't belong in a micro-batching library.

**Evidence:**

1. **Storage Abstraction**
   - `OverflowStorage<T>` interface is generic but implementation (`InMemoryOverflowStorage`) is application-specific
   - Applications may need disk-based, distributed, or database-backed storage
   - Library shouldn't dictate storage mechanism

2. **Replay Logic**
   - `OverflowStrategy` contains complex replay logic (212 lines in `replayOverflowItems()`)
   - Handles edge cases: null polls, invalid levels, max attempts, consecutive null polls
   - This is application-specific business logic

3. **Lifecycle Management**
   - `onBackpressureEntered/Resolved/Active` callbacks are application concerns
   - Kafka consumer pause/resume is application-specific
   - Library shouldn't manage external system lifecycles

4. **Memory Management**
   - `InMemoryOverflowStorage` has capacity limits, eviction strategies
   - Applications need different policies (TTL, priority, deduplication)
   - Library can't anticipate all use cases

**Example of Application-Specific Logic:**
```java
// From OverflowStrategy.java:212-283
private void replayOverflowItems() {
    int maxReplayAttempts = 1000; // Why 1000? Application-specific
    int consecutiveNullPolls = 0;
    final int maxConsecutiveNullPolls = 3; // Why 3? Application-specific
    
    while (attempts < maxReplayAttempts) {
        // Complex replay logic with application-specific heuristics
        if (level < threshold * 0.5 && !overflowStorage.isEmpty()) {
            // Why 0.5? Application-specific threshold
        }
    }
}
```

### ❌ 2. Violation of Single Responsibility Principle

**MicroBatcher's Core Responsibility:**
- Batch requests efficiently
- Dispatch batches to backend
- Handle backpressure **signaling** (not handling)

**Current State:**
- MicroBatcher manages overflow storage lifecycle
- Monitors backpressure state transitions
- Coordinates replay of overflow items
- Manages pause/resume callbacks

**This is too much responsibility for a batching library.**

### ❌ 3. Tight Coupling to Application Concerns

**OverflowStrategy Dependencies:**
```java
public class OverflowStrategy<T> {
    private final OverflowStorage<T> overflowStorage;  // Application concern
    private final Runnable onPause;                     // Application concern
    private final Runnable onResume;                    // Application concern
    private final Function<T, CompletableFuture<...>> submitFunction; // Circular dependency
}
```

**Circular Dependency:**
- `OverflowStrategy` needs `batcher::submit` to replay items
- This creates a circular dependency: `MicroBatcher` → `OverflowStrategy` → `MicroBatcher`

### ❌ 4. Testing Complexity

**OverflowStrategy Tests:**
- 600+ lines of test code
- Complex mocking of storage, providers, submit functions
- Edge cases: null polls, storage full, invalid levels, replay failures
- This complexity suggests the feature is too application-specific

### ❌ 5. Limited Reusability

**OverflowStrategy Assumptions:**
- Assumes FIFO replay (may not be appropriate for all use cases)
- Assumes immediate replay on resolution (may need rate limiting)
- Assumes single-threaded replay (may need concurrent replay)
- Assumes no deduplication (may need idempotency)
- Assumes no priority (may need priority queues)

**Applications need different policies:**
- Kafka: May need partition-aware replay
- Database: May need transaction-aware replay
- Distributed: May need distributed storage
- High-volume: May need rate-limited replay

### ❌ 6. Maintenance Burden

**Complex Code:**
- `OverflowStrategy`: 285 lines
- `InMemoryOverflowStorage`: 124 lines
- `OverflowStrategySpec`: 600+ lines
- Total: ~1000 lines for a feature that may not belong

**Edge Cases:**
- Race conditions in storage
- Replay failures
- Storage full scenarios
- Invalid backpressure levels
- Null item handling
- Consecutive null polls

**This complexity suggests the feature is too application-specific.**

---

## Recommended Design: Library Should Only Signal

### Core Principle

**The library should detect and signal backpressure, not handle it.**

### Proposed Design

#### 1. Keep: Detection and Signaling

```java
// Library provides detection
public interface BackpressureProvider {
    double getBackpressureLevel();
    String getSourceName();
    Map<String, Object> getDetails();
}

// Library provides simple strategies for signaling
public interface BackpressureStrategy<T> {
    BackpressureResult<T> handle(BackpressureContext<T> context);
}

// Built-in strategies: REJECT and DROP (signaling only)
public class RejectStrategy<T> implements BackpressureStrategy<T> {
    // Rejects with exception - application handles it
}

public class DropStrategy<T> implements BackpressureStrategy<T> {
    // Drops silently - application handles it
}
```

#### 2. Remove: OverflowStrategy

**Rationale:**
- Storage is application-specific
- Replay logic is application-specific
- Lifecycle management is application-specific
- Library can't anticipate all use cases

#### 3. Application Handles Overflow

**Example Application Implementation:**

```java
// Application implements overflow handling
public class ApplicationOverflowHandler<T> {
    private final OverflowStorage<T> storage; // Application chooses storage
    private final MicroBatcher<T> batcher;
    private final BackpressureProvider provider;
    
    public void handleBackpressure(T item, double level) {
        if (level >= threshold) {
            // Application decides: store, reject, or drop
            if (storage.hasCapacity()) {
                storage.add(item);
                pauseKafkaConsumer(); // Application-specific
            } else {
                // Application decides: reject or drop
                throw new BackpressureException("Overflow full");
            }
        }
    }
    
    public void monitorAndReplay() {
        // Application-specific replay logic
        // - Rate limiting
        // - Priority handling
        // - Deduplication
        // - Distributed coordination
    }
}
```

#### 4. Library Provides Hooks

**Optional: Lifecycle Hooks (if needed)**

```java
// Optional: Simple lifecycle hooks (no overflow logic)
public interface BackpressureListener {
    void onBackpressureEntered(BackpressureProvider provider);
    void onBackpressureResolved(BackpressureProvider provider);
}

// Application registers listener
batcher.addBackpressureListener(new ApplicationBackpressureListener() {
    @Override
    public void onBackpressureEntered(BackpressureProvider provider) {
        // Application handles: pause Kafka, initialize overflow, etc.
        kafkaConsumer.pause();
        overflowStorage.initialize();
    }
    
    @Override
    public void onBackpressureResolved(BackpressureProvider provider) {
        // Application handles: replay overflow, resume Kafka, etc.
        replayOverflowItems();
        kafkaConsumer.resume();
    }
});
```

---

## Comparison: Current vs Recommended

| Aspect | Current (With Overflow) | Recommended (Signaling Only) |
|--------|------------------------|------------------------------|
| **Library Responsibility** | Detection + Handling + Storage + Replay | Detection + Signaling only |
| **Application Responsibility** | Configure overflow | Implement overflow handling |
| **Complexity** | High (~1000 lines) | Low (~200 lines) |
| **Flexibility** | Limited (FIFO, immediate replay) | Unlimited (application decides) |
| **Testability** | Complex (many edge cases) | Simple (signaling only) |
| **Maintainability** | High (application-specific logic) | Low (focused responsibility) |
| **Reusability** | Limited (assumes use cases) | High (applications customize) |

---

## Migration Path

### Phase 1: Deprecate OverflowStrategy (0.0.8)

```java
/**
 * @deprecated Overflow handling should be implemented by the application.
 * Use {@link RejectStrategy} or {@link DropStrategy} and handle overflow
 * in application code. This will be removed in 0.1.0.
 */
@Deprecated
public class OverflowStrategy<T> implements LifecycleAwareStrategy<T> {
    // ...
}
```

### Phase 2: Add Lifecycle Hooks (0.0.9)

```java
// Simple hooks for applications to implement overflow
public interface BackpressureListener {
    void onBackpressureEntered(BackpressureProvider provider);
    void onBackpressureResolved(BackpressureProvider provider);
}

// MicroBatcher supports listeners
public void addBackpressureListener(BackpressureListener listener);
public void removeBackpressureListener(BackpressureListener listener);
```

### Phase 3: Remove OverflowStrategy (0.1.0)

- Remove `OverflowStrategy` class
- Remove `OverflowStorage` interface (or move to examples)
- Remove `InMemoryOverflowStorage` (or move to examples)
- Remove `LifecycleAwareStrategy` (replace with `BackpressureListener`)
- Update documentation with application examples

---

## Examples: Application-Level Overflow

### Example 1: Kafka Consumer with Overflow

```java
// Application implements overflow
public class KafkaConsumerWithOverflow {
    private final MicroBatcher<String> batcher;
    private final Queue<String> overflowQueue = new LinkedBlockingQueue<>(10000);
    private final BackpressureProvider provider;
    private volatile boolean backpressureActive = false;
    
    public KafkaConsumerWithOverflow() {
        // Use RejectStrategy - library signals, application handles
        BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
        
        BatcherConfig config = BatcherConfig.builder()
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build();
            
        batcher = new MicroBatcher<>(backend, config);
        
        // Application monitors backpressure
        startBackpressureMonitoring();
    }
    
    private void startBackpressureMonitoring() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            double level = provider.getBackpressureLevel();
            boolean wasActive = backpressureActive;
            backpressureActive = (level >= 0.7);
            
            if (!wasActive && backpressureActive) {
                // Entered backpressure: pause Kafka
                kafkaConsumer.pause(consumer.assignment());
            } else if (wasActive && !backpressureActive) {
                // Resolved: resume Kafka and replay overflow
                kafkaConsumer.resume(consumer.assignment());
                replayOverflow();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
    
    public void processRecord(String record) {
        try {
            batcher.submit(record);
        } catch (BackpressureException e) {
            // Library signaled backpressure - application handles overflow
            if (overflowQueue.offer(record)) {
                // Stored to overflow
            } else {
                // Overflow full - application decides: log, alert, or drop
                logger.error("Overflow full, dropping record: {}", record);
            }
        }
    }
    
    private void replayOverflow() {
        // Application-specific replay logic
        // - Rate limiting
        // - Priority handling
        // - Error handling
        while (!overflowQueue.isEmpty() && provider.getBackpressureLevel() < 0.7) {
            String item = overflowQueue.poll();
            if (item != null) {
                try {
                    batcher.submit(item);
                } catch (BackpressureException e) {
                    // Still under pressure, put back
                    overflowQueue.offerFirst(item);
                    break;
                }
            }
        }
    }
}
```

### Example 2: Simple Reject Handling

```java
// Application doesn't need overflow - just handles rejection
public class SimpleApplication {
    private final MicroBatcher<String> batcher;
    
    public SimpleApplication() {
        // Use RejectStrategy - library signals, application handles
        BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7);
        
        BatcherConfig config = BatcherConfig.builder()
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build();
            
        batcher = new MicroBatcher<>(backend, config);
    }
    
    public void processItem(String item) {
        try {
            batcher.submit(item).thenAccept(result -> {
                // Success
            }).exceptionally(error -> {
                // Application handles rejection
                if (error instanceof BackpressureException) {
                    // Log, alert, or retry with backoff
                    logger.warn("Backpressure detected, item rejected: {}", item);
                    scheduleRetry(item);
                }
                return null;
            });
        } catch (BackpressureException e) {
            // Synchronous rejection - application handles
            handleRejection(item, e);
        }
    }
    
    private void handleRejection(String item, BackpressureException e) {
        // Application-specific handling:
        // - Retry with exponential backoff
        // - Send to dead letter queue
        // - Alert monitoring system
        // - Drop if non-critical
    }
}
```

---

## Recommendations

### ✅ Immediate Actions

1. **Keep Core Design**
   - `BackpressureProvider`: Excellent separation of concerns
   - `BackpressureStrategy`: Good interface, but limit to signaling
   - `RejectStrategy` and `DropStrategy`: Keep (they're simple signaling)

2. **Deprecate OverflowStrategy**
   - Mark as `@Deprecated` in 0.0.8
   - Document migration path
   - Provide application examples

3. **Add Lifecycle Hooks (Optional)**
   - Simple `BackpressureListener` interface
   - Applications implement overflow using hooks
   - Library doesn't manage overflow

### ✅ Long-term Actions

1. **Remove OverflowStrategy in 0.1.0**
   - Move to examples if needed
   - Focus library on batching + signaling

2. **Enhance Documentation**
   - Clear examples of application-level overflow
   - Best practices for handling backpressure
   - Migration guide from OverflowStrategy

3. **Consider Separate Module (Optional)**
   - If overflow is common, create `vortex-overflow` module
   - Keep core library focused
   - Applications opt-in if needed

---

## Conclusion

### Key Findings

1. **OverflowStrategy doesn't belong in the library**
   - Introduces application-specific concerns (storage, replay, lifecycle)
   - Violates single responsibility principle
   - Limits flexibility and reusability
   - Increases maintenance burden

2. **Library should focus on signaling**
   - Detection: `BackpressureProvider` ✅
   - Signaling: `RejectStrategy`, `DropStrategy` ✅
   - Handling: Application responsibility ❌ (currently in library)

3. **Recommended approach**
   - Keep: Detection and simple signaling strategies
   - Remove: OverflowStrategy, OverflowStorage, LifecycleAwareStrategy
   - Add: Optional lifecycle hooks (if needed)
   - Document: Application-level overflow examples

### Benefits of Recommended Approach

- **Simpler library**: Focused on core responsibility (batching + signaling)
- **More flexible**: Applications implement overflow according to their needs
- **Better separation**: Library signals, application handles
- **Easier maintenance**: Less application-specific code in library
- **Better testability**: Simpler, focused tests

### Migration Impact

- **Breaking change**: Yes (removal of OverflowStrategy)
- **Migration effort**: Low (applications implement overflow)
- **Timeline**: Deprecate in 0.0.8, remove in 0.1.0
- **Documentation**: Provide examples and migration guide

---

## Appendix: Code Metrics

### Current Backpressure Package

| Component | Lines of Code | Complexity |
|-----------|---------------|------------|
| `OverflowStrategy` | 285 | High |
| `InMemoryOverflowStorage` | 124 | Medium |
| `OverflowStrategySpec` | 600+ | High |
| `LifecycleAwareStrategy` | 111 | Low |
| `OverflowStorage` | 67 | Low |
| **Total Overflow Code** | **~1187 lines** | **High** |

### Recommended (Signaling Only)

| Component | Lines of Code | Complexity |
|-----------|---------------|------------|
| `RejectStrategy` | 66 | Low |
| `DropStrategy` | 55 | Low |
| `BackpressureProvider` | 65 | Low |
| `BackpressureStrategy` | 59 | Low |
| **Total Signaling Code** | **~245 lines** | **Low** |

**Reduction: ~80% less code, significantly lower complexity**

---

**Analysis Complete**

