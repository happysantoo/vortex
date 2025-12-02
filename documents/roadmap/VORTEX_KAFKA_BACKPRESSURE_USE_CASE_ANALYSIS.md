# Kafka Consumer Backpressure Use Case Analysis

**Version**: 0.0.4  
**Status**: Design Analysis  
**Date**: 2024

## Use Case Description

A Kafka consumer application using Vortex needs sophisticated backpressure handling:

1. **Overflow to Temporary Memory**: When backpressure is detected, items should be stored in a temporary memory area instead of being rejected or dropped
2. **Pause Kafka Consumer**: The Kafka consumer should be paused to stop receiving new messages
3. **Monitor Backpressure**: System should continuously check if queue pressure has resolved
4. **Replay from Overflow**: Once backpressure is resolved, items from the temporary memory store should be replayed
5. **Resume Kafka Consumer**: After replay completes, the Kafka consumer should resume

## Current Design Analysis

### What the Current Phase 1 Design Provides

✅ **Backpressure Detection**
- `BackpressureProvider` can detect backpressure levels
- `QueueDepthBackpressureProvider` monitors queue depth
- `CompositeBackpressureProvider` can combine multiple sources

✅ **Backpressure Handling**
- `BackpressureStrategy` interface for handling decisions
- `DropStrategy` and `RejectStrategy` for immediate actions
- Per-item decision making

❌ **What's Missing**

1. **No Overflow Mechanism**: Current strategies only support ACCEPT, REJECT, DROP - no overflow storage
2. **No Lifecycle Management**: Strategies are stateless, called per-item, no enter/exit events
3. **No Consumer Control**: No way to pause/resume external systems (Kafka consumer)
4. **No Continuous Monitoring**: No background monitoring of backpressure state changes
5. **No Replay Mechanism**: No built-in replay from overflow storage

### Can Current Design Support This Use Case?

**Short Answer**: **Partially, but requires significant workarounds and external coordination.**

**Analysis**:

The current design can support this use case, but it requires:
1. **Custom Strategy**: Implement a `KafkaOverflowStrategy` that stores items to temp memory
2. **External Coordination**: Application code must manage Kafka consumer pause/resume
3. **External Monitoring**: Application code must poll backpressure level and trigger replay
4. **External Replay**: Application code must implement replay logic

**Problems with this approach**:
- ❌ Tight coupling between strategy and application code
- ❌ Complex coordination logic in application
- ❌ No lifecycle guarantees (when to pause, when to resume)
- ❌ Race conditions possible (backpressure changes during replay)
- ❌ Difficult to test and maintain

---

## Enhanced Design Proposal

### Option 1: Lifecycle-Aware Strategy (Recommended)

Extend `BackpressureStrategy` with lifecycle methods to support stateful strategies:

```java
/**
 * Enhanced strategy interface with lifecycle management.
 * 
 * <p>Supports strategies that need to manage state transitions,
 * such as overflow storage, consumer pause/resume, and replay.
 */
public interface BackpressureStrategy<T> {
    /**
     * Handles an item when backpressure is detected.
     * 
     * @param context the backpressure context
     * @return result indicating how the item was handled
     */
    BackpressureResult<T> handle(BackpressureContext<T> context);
    
    /**
     * Called when backpressure is first detected (enters high state).
     * 
     * <p>Use this to initialize overflow storage, pause consumers, etc.
     * Called once when backpressure level crosses threshold from below to above.
     * 
     * @param provider the backpressure provider
     */
    default void onBackpressureEntered(BackpressureProvider provider) {
        // Default: no-op
    }
    
    /**
     * Called when backpressure is resolved (exits high state).
     * 
     * <p>Use this to trigger replay, resume consumers, etc.
     * Called once when backpressure level crosses threshold from above to below.
     * 
     * @param provider the backpressure provider
     */
    default void onBackpressureResolved(BackpressureProvider provider) {
        // Default: no-op
    }
    
    /**
     * Called periodically while backpressure is active.
     * 
     * <p>Use this to check if conditions are met for replay/resume.
     * Called at configurable intervals (e.g., every 100ms) while backpressure >= threshold.
     * 
     * @param provider the backpressure provider
     */
    default void onBackpressureActive(BackpressureProvider provider) {
        // Default: no-op
    }
}
```

**Benefits**:
- ✅ Lifecycle management built into strategy
- ✅ Clear state transitions (enter, active, resolve)
- ✅ Strategy can manage its own state
- ✅ Backward compatible (default methods)

**Implementation in MicroBatcher**:
```java
private volatile boolean backpressureActive = false;
private final ScheduledExecutorService backpressureMonitor;

// In submit() method:
if (backpressureProvider != null && backpressureStrategy != null) {
    double backpressure = backpressureProvider.getBackpressureLevel();
    double threshold = getThreshold(backpressureStrategy);
    
    boolean wasActive = backpressureActive;
    boolean isActive = backpressure >= threshold;
    
    // State transition: entering backpressure
    if (!wasActive && isActive) {
        backpressureActive = true;
        backpressureStrategy.onBackpressureEntered(backpressureProvider);
        // Start monitoring
        scheduleBackpressureMonitoring();
    }
    
    // State transition: exiting backpressure
    if (wasActive && !isActive) {
        backpressureActive = false;
        backpressureStrategy.onBackpressureResolved(backpressureProvider);
        // Stop monitoring
        cancelBackpressureMonitoring();
    }
    
    // Handle item
    BackpressureResult<T> result = backpressureStrategy.handle(context);
    // ... rest of logic
}

private void scheduleBackpressureMonitoring() {
    backpressureMonitor.scheduleAtFixedRate(() -> {
        if (backpressureActive && backpressureStrategy != null) {
            double level = backpressureProvider.getBackpressureLevel();
            if (level >= getThreshold(backpressureStrategy)) {
                backpressureStrategy.onBackpressureActive(backpressureProvider);
            }
        }
    }, 100, 100, TimeUnit.MILLISECONDS);
}
```

### Option 2: Overflow Strategy with Built-in Support

Create a specialized `OverflowStrategy` that handles overflow, monitoring, and replay:

```java
/**
 * Strategy that overflows items to temporary storage when backpressure is high.
 * 
 * <p>Lifecycle:
 * <ol>
 *   <li>When backpressure enters: Pause consumer, start storing items to overflow</li>
 *   <li>While backpressure active: Continue storing items, monitor for resolution</li>
 *   <li>When backpressure resolves: Replay items from overflow, resume consumer</li>
 * </ol>
 */
public class OverflowStrategy<T> implements LifecycleAwareStrategy<T> {
    private final double threshold;
    private final OverflowStorage<T> overflowStorage;
    private final Runnable onPause;  // Optional callback for pausing consumer
    private final Runnable onResume; // Optional callback for resuming consumer
    private final BackpressureProvider backpressureProvider;
    private final Function<T, CompletableFuture<BatchResult<T>>> submitFunction;
    private volatile boolean overflowActive = false;
    
    public OverflowStrategy(
            double threshold,
            OverflowStorage<T> overflowStorage,
            BackpressureProvider backpressureProvider,
            Function<T, CompletableFuture<BatchResult<T>>> submitFunction) {
        this(threshold, overflowStorage, backpressureProvider, submitFunction, null, null);
    }
    
    public OverflowStrategy(
            double threshold,
            OverflowStorage<T> overflowStorage,
            BackpressureProvider backpressureProvider,
            Function<T, CompletableFuture<BatchResult<T>>> submitFunction,
            Runnable onPause,
            Runnable onResume) {
        this.threshold = threshold;
        this.overflowStorage = overflowStorage;
        this.backpressureProvider = backpressureProvider;
        this.submitFunction = submitFunction;
        this.onPause = onPause;
        this.onResume = onResume;
    }
    
    @Override
    public BackpressureResult<T> handle(BackpressureContext<T> context) {
        if (context.backpressureLevel() >= threshold) {
            // Store to overflow
            overflowStorage.add(context.item());
            return BackpressureResult.drop(context.item()); // Drop from normal flow
        }
        return BackpressureResult.accept(context.item());
    }
    
    @Override
    public void onBackpressureEntered(BackpressureProvider provider) {
        overflowActive = true;
        if (onPause != null) {
            onPause.run(); // Pause Kafka consumer (or any other action)
        }
    }
    
    @Override
    public void onBackpressureResolved(BackpressureProvider provider) {
        overflowActive = false;
        replayOverflowItems();
        if (onResume != null) {
            onResume.run(); // Resume Kafka consumer (or any other action)
        }
    }
    
    @Override
    public void onBackpressureActive(BackpressureProvider provider) {
        // Check if we can start replaying (e.g., queue depth < 50% of threshold)
        double level = provider.getBackpressureLevel();
        if (level < threshold * 0.5 && !overflowStorage.isEmpty()) {
            // Start gradual replay
            replayOverflowItems();
        }
    }
    
    private void replayOverflowItems() {
        while (!overflowStorage.isEmpty() && 
               backpressureProvider.getBackpressureLevel() < threshold) {
            T item = overflowStorage.poll();
            if (item != null) {
                submitFunction.apply(item);
            }
        }
    }
}

/**
 * Interface for overflow storage.
 */
public interface OverflowStorage<T> {
    void add(T item);
    T poll();
    boolean isEmpty();
    int size();
}
```

**Benefits**:
- ✅ Complete solution for overflow use case
- ✅ Encapsulates all overflow logic
- ✅ Reusable for other overflow scenarios
- ✅ Testable in isolation
- ✅ **Simpler API** - no separate ConsumerController interface
- ✅ **More flexible** - callbacks can do anything (pause Kafka, notify monitoring, etc.)

### Option 3: Backpressure Manager (Higher-Level Component)

Create a separate `BackpressureManager` that coordinates backpressure handling:

```java
/**
 * Manages backpressure lifecycle and coordinates strategy execution.
 * 
 * <p>Provides:
 * <ul>
 *   <li>Continuous monitoring of backpressure level</li>
 *   <li>State transition management (enter/exit backpressure)</li>
 *   <li>Lifecycle callbacks for strategies</li>
 *   <li>Integration with external systems (Kafka, etc.)</li>
 * </ul>
 */
public class BackpressureManager<T> {
    private final BackpressureProvider provider;
    private final BackpressureStrategy<T> strategy;
    private final ScheduledExecutorService monitor;
    private volatile boolean active = false;
    private final double threshold;
    
    public BackpressureManager(
            BackpressureProvider provider,
            BackpressureStrategy<T> strategy,
            double threshold,
            Duration checkInterval) {
        this.provider = provider;
        this.strategy = strategy;
        this.threshold = threshold;
        this.monitor = Executors.newSingleThreadScheduledExecutor();
        
        // Start monitoring
        monitor.scheduleAtFixedRate(this::checkBackpressure, 
            0, checkInterval.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    private void checkBackpressure() {
        double level = provider.getBackpressureLevel();
        boolean isActive = level >= threshold;
        
        if (!active && isActive) {
            // Entering backpressure
            active = true;
            if (strategy instanceof LifecycleAwareStrategy) {
                ((LifecycleAwareStrategy<T>) strategy).onBackpressureEntered(provider);
            }
        } else if (active && !isActive) {
            // Exiting backpressure
            active = false;
            if (strategy instanceof LifecycleAwareStrategy) {
                ((LifecycleAwareStrategy<T>) strategy).onBackpressureResolved(provider);
            }
        } else if (active) {
            // Active backpressure
            if (strategy instanceof LifecycleAwareStrategy) {
                ((LifecycleAwareStrategy<T>) strategy).onBackpressureActive(provider);
            }
        }
    }
    
    public BackpressureResult<T> handleItem(T item) {
        double level = provider.getBackpressureLevel();
        BackpressureContext<T> context = new BackpressureContext<>(
            item, level, provider
        );
        return strategy.handle(context);
    }
    
    public void shutdown() {
        monitor.shutdown();
    }
}
```

**Benefits**:
- ✅ Separates monitoring from strategy
- ✅ Can be used independently of MicroBatcher
- ✅ Supports multiple strategies
- ✅ More flexible

**Drawbacks**:
- ❌ Additional component to maintain
- ❌ More complex integration

---

## Recommended Approach: Hybrid Solution

### Phase 1 (0.0.4): Foundation + Overflow Strategy

1. **Keep Current Design**: Simple, per-item strategy handling
2. **Add Lifecycle Interface**: Optional `LifecycleAwareStrategy` interface
3. **Add OverflowStrategy**: Built-in overflow strategy for common use case
4. **Add Monitoring in MicroBatcher**: Basic state transition tracking

### Implementation Plan

#### Step 1: Add Lifecycle Interface

```java
/**
 * Optional interface for strategies that need lifecycle management.
 */
public interface LifecycleAwareStrategy<T> extends BackpressureStrategy<T> {
    void onBackpressureEntered(BackpressureProvider provider);
    void onBackpressureResolved(BackpressureProvider provider);
    default void onBackpressureActive(BackpressureProvider provider) {
        // Optional: periodic checks while active
    }
}
```

#### Step 2: Add Overflow Support Interface

```java
public interface OverflowStorage<T> {
    void add(T item);
    T poll();
    boolean isEmpty();
    int size();
    void clear();
}
```

**Note**: No `ConsumerController` interface needed. The `OverflowStrategy` accepts optional `Runnable` callbacks for pause/resume, making it simpler and more flexible.

#### Step 3: Implement OverflowStrategy

```java
public class OverflowStrategy<T> implements LifecycleAwareStrategy<T> {
    // Implementation as shown in Option 2
}
```

#### Step 4: Add Monitoring to MicroBatcher

```java
// Track backpressure state
private volatile boolean backpressureActive = false;
private final ScheduledExecutorService backpressureMonitor;

// In constructor:
if (backpressureStrategy instanceof LifecycleAwareStrategy) {
    startBackpressureMonitoring();
}

private void startBackpressureMonitoring() {
    backpressureMonitor = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "vortex-backpressure-monitor");
            t.setDaemon(true);
            return t;
        }
    );
    
    backpressureMonitor.scheduleAtFixedRate(() -> {
        if (backpressureProvider != null && 
            backpressureStrategy instanceof LifecycleAwareStrategy) {
            double level = backpressureProvider.getBackpressureLevel();
            double threshold = getThreshold(backpressureStrategy);
            
            boolean isActive = level >= threshold;
            
            if (!backpressureActive && isActive) {
                backpressureActive = true;
                ((LifecycleAwareStrategy<T>) backpressureStrategy)
                    .onBackpressureEntered(backpressureProvider);
            } else if (backpressureActive && !isActive) {
                backpressureActive = false;
                ((LifecycleAwareStrategy<T>) backpressureStrategy)
                    .onBackpressureResolved(backpressureProvider);
            } else if (backpressureActive) {
                ((LifecycleAwareStrategy<T>) backpressureStrategy)
                    .onBackpressureActive(backpressureProvider);
            }
        }
    }, 0, 100, TimeUnit.MILLISECONDS);
}
```

---

## Kafka Consumer Integration Example

```java
// 1. Create overflow storage (in-memory queue)
OverflowStorage<String> overflowStorage = new InMemoryOverflowStorage<>(1000);

// 2. Create queue depth provider
BackpressureProvider queueProvider = new QueueDepthBackpressureProvider(
    () -> batcher.diagnostics().getQueueDepth(),
    config.getMaxQueueSize()
);

// 3. Create overflow strategy with pause/resume callbacks
OverflowStrategy<String> overflowStrategy = new OverflowStrategy<>(
    0.7, // threshold
    overflowStorage,
    queueProvider,
    batcher::submit, // submit function for replay
    () -> kafkaConsumer.pause(consumer.assignment()), // onPause callback
    () -> kafkaConsumer.resume(consumer.assignment())  // onResume callback
);

// 4. Create batcher with overflow strategy
MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
    backend,
    config,
    queueProvider,
    overflowStrategy
);

// 5. In Kafka consumer loop:
while (running) {
    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        // Submit to batcher - overflow strategy will handle backpressure
        batcher.submit(record.value());
    }
}
```

**How it works**:
1. **Normal Operation**: Items are accepted and batched normally
2. **Backpressure Detected**: 
   - `onBackpressureEntered()` is called
   - Kafka consumer is paused
   - Items start going to overflow storage
3. **While Active**: 
   - `onBackpressureActive()` is called periodically
   - Items continue to overflow
   - May start gradual replay if pressure reduces
4. **Backpressure Resolved**:
   - `onBackpressureResolved()` is called
   - Items are replayed from overflow
   - Kafka consumer is resumed

---

## Design Decision: What to Include in 0.0.4?

### Option A: Minimal (Current Plan)
- ✅ Core interfaces (Provider, Strategy, Context, Result)
- ✅ Basic strategies (Drop, Reject)
- ✅ Basic providers (QueueDepth, Composite)
- ❌ No overflow support
- ❌ No lifecycle management

**Verdict**: ❌ **Insufficient for Kafka use case**

### Option B: With Overflow (Recommended)
- ✅ Core interfaces
- ✅ Basic strategies (Drop, Reject)
- ✅ Basic providers
- ✅ **LifecycleAwareStrategy interface**
- ✅ **OverflowStrategy implementation**
- ✅ **OverflowStorage interface** (no ConsumerController - uses simple callbacks)
- ✅ **Monitoring in MicroBatcher**

**Verdict**: ✅ **Supports Kafka use case with simpler design**

### Option C: Full Feature Set
- ✅ Everything in Option B
- ✅ BackpressureManager component
- ✅ Multiple overflow storage implementations
- ✅ Advanced replay strategies

**Verdict**: ⚠️ **Too much for 0.0.4, defer to 0.0.5**

---

## Recommendation

**Implement Option B for 0.0.4**:

1. **Add LifecycleAwareStrategy interface** (optional, backward compatible)
2. **Add OverflowStrategy** (built-in support for overflow use case)
3. **Add OverflowStorage and ConsumerController interfaces** (pluggable)
4. **Add monitoring to MicroBatcher** (only if strategy is LifecycleAware)
5. **Provide example implementations** (InMemoryOverflowStorage, KafkaConsumerController)

**Benefits**:
- ✅ Supports Kafka use case out of the box
- ✅ Backward compatible (existing strategies work unchanged)
- ✅ Extensible (can add custom overflow storage)
- ✅ Testable (all components can be tested independently)
- ✅ Reasonable scope for 0.0.4

**Estimated Additional Work**:
- ~200 lines for interfaces
- ~300 lines for OverflowStrategy
- ~100 lines for monitoring in MicroBatcher
- ~150 lines for example implementations
- ~400 lines for tests

**Total**: ~1,150 additional lines (vs ~400 for basic backpressure)

---

## Updated Implementation Plan

### Phase 1: Core Interfaces (Enhanced)
- [x] `BackpressureProvider` interface
- [x] `BackpressureStrategy` interface
- [x] `BackpressureContext` record
- [x] `BackpressureResult` enum + record
- [x] `BackpressureException` class
- [ ] **`LifecycleAwareStrategy` interface (NEW)**
- [ ] **`OverflowStorage` interface (NEW)**

### Phase 2: Built-in Providers (Unchanged)
- [x] `QueueDepthBackpressureProvider`
- [x] `CompositeBackpressureProvider`

### Phase 3: Built-in Strategies (Enhanced)
- [x] `DropStrategy`
- [x] `RejectStrategy`
- [ ] **`OverflowStrategy` (NEW)**

### Phase 4: MicroBatcher Integration (Enhanced)
- [x] Constructor overload
- [x] `withBackpressure()` factory method
- [x] `submit()` integration
- [ ] **Backpressure monitoring (NEW)**
- [ ] **Lifecycle callback support (NEW)**

### Phase 5: Example Implementations
- [ ] `InMemoryOverflowStorage` (NEW)
- [ ] `KafkaOverflowExample` (NEW - shows pause/resume callbacks)

---

## Conclusion

**Current Phase 1 design CAN support the Kafka use case, but requires significant application-level coordination.**

**Recommended enhancement**: Add lifecycle-aware strategies and overflow support to make this use case first-class, reducing complexity for users and improving maintainability.

**Decision**: Implement Option B (LifecycleAwareStrategy + OverflowStrategy) in 0.0.4 to fully support the Kafka consumer use case.

