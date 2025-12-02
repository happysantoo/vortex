# Vortex Micro-Batching Library - Release Notes 0.0.4

**Release Date**: 2024  
**Status**: Stable  
**Java Version**: 21+

## Overview

Version 0.0.4 introduces **comprehensive backpressure handling** capabilities, enabling the library to gracefully handle system overload scenarios. This release adds sophisticated backpressure detection, multiple handling strategies, and lifecycle-aware overflow management - making it production-ready for high-throughput systems like Kafka consumers.

## 🎯 Key Features

### 1. Backpressure Detection System

**New Interfaces:**
- `BackpressureProvider` - Generic interface for detecting system pressure (0.0-1.0 scale)
- `BackpressureStrategy` - Strategy pattern for handling items when backpressure is detected
- `LifecycleAwareStrategy` - Optional interface for strategies needing lifecycle management

**Built-in Providers:**
- `QueueDepthBackpressureProvider` - Monitors internal queue depth with linear scaling
- `CompositeBackpressureProvider` - Combines multiple backpressure sources (uses maximum)

**Built-in Strategies:**
- `DropStrategy` - Silently drops items when backpressure exceeds threshold
- `RejectStrategy` - Rejects items with `BackpressureException` when threshold exceeded
- `OverflowStrategy` - Stores items to overflow storage and manages external consumer lifecycle

### 2. Overflow Management

**New Components:**
- `OverflowStorage` - Interface for temporary storage during backpressure
- `InMemoryOverflowStorage` - In-memory implementation using `ConcurrentLinkedQueue`
- `OverflowStrategy` - Advanced strategy that:
  - Stores items to overflow when backpressure is high
  - Pauses external consumers (e.g., Kafka) via callbacks
  - Monitors backpressure resolution
  - Replays items from overflow when capacity becomes available
  - Resumes external consumers automatically

### 3. Lifecycle Management

**Lifecycle Callbacks:**
- `onBackpressureEntered()` - Called when backpressure first detected
- `onBackpressureResolved()` - Called when backpressure resolves
- `onBackpressureActive()` - Called periodically while backpressure is active

**Automatic Monitoring:**
- Background monitoring thread (100ms interval) for lifecycle-aware strategies
- Automatic state transition detection
- Thread-safe state management

### 4. Enhanced Metrics

**New Metrics:**
- `vortex.backpressure.rejected` - Counter for items rejected due to backpressure
- `vortex.backpressure.dropped` - Counter for items dropped due to backpressure
- Existing metrics remain unchanged

### 5. Integration Points

**MicroBatcher Enhancements:**
- Early backpressure check in `submit()` method (before queue offer)
- Factory methods: `withBackpressure()` for convenient setup
- Backward compatible - all existing code continues to work
- Optional feature - null-safe, no breaking changes

**BatcherConfig Enhancements:**
- `backpressureProvider()` builder method
- `backpressureStrategy()` builder method
- Configuration via builder pattern

## 📦 New Classes and Interfaces

### Core Interfaces

1. **`BackpressureProvider`** (`com.vajrapulse.vortex.backpressure`)
   - `double getBackpressureLevel()` - Returns 0.0-1.0 pressure level
   - `String getSourceName()` - Human-readable source name
   - `Map<String, Object> getDetails()` - Optional diagnostic details

2. **`BackpressureStrategy<T>`** (`com.vajrapulse.vortex.backpressure`)
   - `BackpressureResult<T> handle(BackpressureContext<T> context)` - Handles item when backpressure detected

3. **`LifecycleAwareStrategy<T>`** (`com.vajrapulse.vortex.backpressure`)
   - Extends `BackpressureStrategy`
   - `void onBackpressureEntered(BackpressureProvider provider)`
   - `void onBackpressureResolved(BackpressureProvider provider)`
   - `void onBackpressureActive(BackpressureProvider provider)` - Default no-op

### Supporting Types

4. **`BackpressureContext<T>`** - Record containing item, backpressure level, and provider
5. **`BackpressureResult<T>`** - Record with action (ACCEPT/REJECT/DROP), item, and reason
6. **`BackpressureAction`** - Enum: ACCEPT, REJECT, DROP
7. **`BackpressureException`** - Custom exception for backpressure rejections

### Built-in Implementations

8. **`QueueDepthBackpressureProvider`** - Linear scaling based on queue depth
9. **`CompositeBackpressureProvider`** - Combines multiple providers (maximum aggregation)
10. **`DropStrategy<T>`** - Drops items silently above threshold
11. **`RejectStrategy<T>`** - Rejects items with exception above threshold
12. **`OverflowStrategy<T>`** - Overflow management with lifecycle callbacks
13. **`InMemoryOverflowStorage<T>`** - In-memory overflow storage

## 🔧 API Changes

### New Factory Methods

```java
// Factory method with default MeterRegistry
MicroBatcher<T> withBackpressure(
    Backend<T> backend,
    BatcherConfig config,
    BackpressureProvider provider,
    BackpressureStrategy<T> strategy
)

// Factory method with custom MeterRegistry
MicroBatcher<T> withBackpressure(
    Backend<T> backend,
    BatcherConfig config,
    MeterRegistry meterRegistry,
    BackpressureProvider provider,
    BackpressureStrategy<T> strategy
)
```

### New Builder Methods

```java
BatcherConfig.builder()
    .backpressureProvider(provider)
    .backpressureStrategy(strategy)
    .build()
```

### New Metrics Methods

```java
MetricsManager:
- void recordBackpressureRejected()
- void recordBackpressureDropped()

MetricsProvider:
- long getTotalBackpressureRejected()
- long getTotalBackpressureDropped()
```

## 📝 Usage Examples

### Basic Backpressure with Drop Strategy

```java
Backend<String> backend = batch -> {
    // Process batch
    return new BatchResult<>(successes, failures);
};

BackpressureProvider provider = new QueueDepthBackpressureProvider(
    () -> batcher.diagnostics().getQueueDepth(),
    maxQueueSize
);

BackpressureStrategy<String> strategy = new DropStrategy<>(0.8); // Drop at 80% capacity

MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
    backend,
    config,
    provider,
    strategy
);
```

### Overflow Strategy for Kafka Consumer

```java
OverflowStorage<String> overflow = new InMemoryOverflowStorage<>();

OverflowStrategy<String> strategy = new OverflowStrategy<>(
    0.7, // Threshold
    overflow,
    provider,
    item -> batcher.submit(item), // Resubmit function
    () -> kafkaConsumer.pause(), // Pause callback
    () -> kafkaConsumer.resume() // Resume callback
);

MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
    backend,
    config,
    provider,
    strategy
);
```

### Composite Backpressure Provider

```java
BackpressureProvider queueProvider = new QueueDepthBackpressureProvider(...);
BackpressureProvider connectionPoolProvider = new CustomConnectionPoolProvider(...);

BackpressureProvider composite = new CompositeBackpressureProvider(
    queueProvider,
    connectionPoolProvider
);
```

## 🐛 Bug Fixes

1. **Fixed infinite loop in `OverflowStrategy.replayOverflowItems()`** - Added break when `poll()` returns null
2. **Fixed `NullPointerException` in constructor** - Added null-safe config access before calling methods
3. **Fixed test coverage gaps** - Added comprehensive tests for all new components

## ✅ Improvements

1. **Thread Safety** - All backpressure components are thread-safe
2. **Error Handling** - Fail-safe error handling in strategy execution
3. **Validation** - Input validation for thresholds, capacities, and null values
4. **Documentation** - Comprehensive JavaDoc for all public APIs
5. **Test Coverage** - >90% line coverage for all new components

## 🔄 Migration Guide

### From 0.0.3 to 0.0.4

**No breaking changes!** All existing code continues to work without modification.

**To enable backpressure:**

1. Create a `BackpressureProvider` (e.g., `QueueDepthBackpressureProvider`)
2. Create a `BackpressureStrategy` (e.g., `DropStrategy`, `RejectStrategy`, or `OverflowStrategy`)
3. Use factory method or builder:

```java
// Option 1: Factory method
MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
    backend, config, provider, strategy
);

// Option 2: Builder pattern
BatcherConfig config = BatcherConfig.builder()
    .backpressureProvider(provider)
    .backpressureStrategy(strategy)
    .build();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, config);
```

## 📊 Performance Characteristics

- **Backpressure Check Overhead**: < 1μs per submission (hash-based lookup)
- **Monitoring Thread**: 100ms interval, minimal CPU usage
- **Overflow Storage**: O(1) add/poll operations
- **Composite Provider**: O(n) where n = number of providers

## 🧪 Testing

- **Unit Tests**: 100+ new test cases covering all backpressure scenarios
- **Integration Tests**: Kafka consumer use case validation
- **Coverage**: >90% line coverage for all new components
- **Thread Safety Tests**: Concurrent access validation

## 📚 Documentation

- **JavaDoc**: Complete API documentation
- **Examples**: Kafka consumer example demonstrating overflow strategy
- **Guides**: Backpressure usage guide
- **Architecture**: Design decisions documented

## 🔮 Future Enhancements (0.0.5+)

- **ThrottleStrategy** - Rate-limiting strategy
- **Disk-based OverflowStorage** - For larger overflow scenarios
- **Advanced Replay Strategies** - Gradual replay, priority-based replay
- **Backpressure Metrics Dashboard** - Grafana integration
- **Logarithmic Scaling** - Alternative scaling algorithms

## 🙏 Acknowledgments

Special thanks to the principal engineer review that helped refine the design and ensure production-readiness.

---

**Full Changelog**: See `CHANGELOG.md` for detailed change history.

