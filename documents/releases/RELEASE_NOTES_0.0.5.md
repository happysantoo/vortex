# Vortex Micro-Batching Library - Release Notes 0.0.5

**Release Date**: 2025-12-04  
**Status**: Stable  
**Java Version**: 21+

## Overview

Version 0.0.5 introduces **synchronous submission APIs**, **performance optimizations**, and **enhanced observability** features. This release focuses on improving developer experience, providing immediate rejection visibility, and optimizing backpressure handling for high-throughput scenarios.

## 🎯 Key Features

### 1. Synchronous Submission API (`submitSync()`)

**New Method**: `MicroBatcher.submitSync(T item)`

Immediate visibility of rejections without waiting for batch processing. Perfect for load testing frameworks and scenarios requiring synchronous feedback.

**Features**:
- Returns `ItemResult<T>` immediately (Success or Failure)
- Synchronous backpressure and queue capacity checks
- Thread-safe and non-blocking operations
- **11% faster** than async `submit()` when items are accepted

**Example**:
```java
ItemResult<String> result = batcher.submitSync("item");
if (result instanceof ItemResult.Failure<String> failure) {
    // Immediate rejection - handle immediately
    handleRejection(failure.error());
} else {
    // Item accepted and queued
    // Use submitWithCallback() to track eventual batch result
}
```

**Performance**:
- Accepted items: ~255 ns (11% faster than async)
- With backpressure caching: ~245 ns (15% faster)
- Rejected items: ~25.6 μs (acceptable for synchronous checks)

### 2. Enhanced `submitWithCallback()` Method

**Improved Implementation**: Better immediate rejection handling

The `submitWithCallback()` method now provides immediate callback invocation for rejections, while still supporting eventual callbacks for accepted items.

**Features**:
- Immediate callback for rejections (queue full, backpressure)
- Eventual callback for accepted items (when batch completes)
- Exception handling in callbacks
- Hybrid approach support (combine with `submitSync()`)

**Example**:
```java
batcher.submitWithCallback("item", (item, result) -> {
    if (result instanceof ItemResult.Failure<String> failure) {
        // Immediate rejection - callback invoked immediately
        handleRejection(failure.error());
    } else {
        // Item processed - callback invoked when batch completes
        handleSuccess(item);
    }
});
```

### 3. Backpressure Level Caching

**Performance Optimization**: TTL-based caching for backpressure levels

Reduces the overhead of calling backpressure providers on every submission by caching backpressure levels with a configurable TTL.

**Features**:
- Thread-safe TTL-based caching
- Configurable cache TTL (default: 50ms)
- Automatic cache invalidation
- **~95% reduction** in provider calls in high-throughput scenarios

**Configuration**:
```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .backpressureCacheTtl(Duration.ofMillis(100)) // Custom TTL
    .build();
```

**Performance Impact**:
- High-throughput scenarios: ~95% reduction in provider calls
- Low-latency scenarios: Configurable TTL for optimal balance
- Memory overhead: Minimal (single cached value)

### 4. Enhanced Error Metrics

**New Metrics**: Additional observability for error scenarios

Three new metrics provide better visibility into error conditions and edge cases:

1. **`vortex.backpressure.check.failures`**
   - Counter for exceptions during backpressure checks
   - Helps identify provider issues or configuration problems

2. **`vortex.backpressure.invalid.levels`**
   - Counter for invalid backpressure levels (NaN, out of range)
   - Helps identify provider bugs or data quality issues

3. **`vortex.queue.offer.failures`**
   - Counter for queue offer failures (race conditions)
   - Helps identify queue capacity issues and timing problems

**Usage**:
```java
MeterRegistry registry = batcher.getMeterRegistry();
Counter checkFailures = registry.counter("vortex.backpressure.check.failures");
long failures = checkFailures.count();
```

### 5. OpenTelemetry Distributed Tracing Integration

**Optional Integration**: `OpenTelemetryTracingHook` class

Provides distributed tracing support for OpenTelemetry-compatible systems without requiring OpenTelemetry as a mandatory dependency.

**Features**:
- Reflection-based implementation (works without OpenTelemetry in classpath)
- Creates spans for key operations (submit, batch dispatch, retry)
- Propagates trace context through batch processing
- Graceful degradation when OpenTelemetry unavailable

**Usage**:
```java
OpenTelemetryTracingHook otelHook = new OpenTelemetryTracingHook();

BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .tracingHook(otelHook)
    .build();
```

**Note**: If OpenTelemetry is not in the classpath, the hook will silently do nothing, allowing your application to work without OpenTelemetry dependencies.

### 6. Individual Item Metrics Fix

**Bug Fix**: Corrected metrics for per-item tracking

Fixed incorrect latency recording in individual item metrics. Metrics now accurately distinguish between queue wait time and full processing latency.

**Fixed Metrics**:
- `vortex.item.wait.time` - Now correctly records **only queue wait time**
- `vortex.item.submit.latency` - Records **full submit-to-completion latency**

**Before** (Incorrect):
- `itemWaitTime` was recording full latency (queue wait + backend processing)

**After** (Correct):
- `itemWaitTime` records only queue wait time
- `itemSubmitLatency` records full latency (queue wait + backend processing)

**Impact**: Metrics now provide accurate observability for per-item tracking, enabling better performance analysis and optimization.

## 📦 New Classes and Interfaces

### Core Classes

1. **`BackpressureLevelCache`** (`com.vajrapulse.vortex.backpressure`)
   - TTL-based caching for backpressure levels
   - Thread-safe implementation
   - Automatic cache invalidation

2. **`OpenTelemetryTracingHook`** (`com.vajrapulse.vortex.tracing`)
   - OpenTelemetry integration hook
   - Reflection-based (optional dependency)
   - Graceful degradation

### New Types

3. **`ItemResult<T>`** - Sealed interface for synchronous results
   - `ItemResult.Success<T>` - Item accepted
   - `ItemResult.Failure<T>` - Item rejected with error

## 🔧 Configuration Enhancements

### New Builder Methods

- `BatcherConfig.backpressureCacheTtl(Duration ttl)` - Configure backpressure cache TTL (default: 50ms)

### New Methods

- `MicroBatcher.submitSync(T item)` - Synchronous submission with immediate result
- `MicroBatcher.getQueueDepth()` - Get current queue depth (helper method)

## 📊 Performance Improvements

### Benchmark Results

**SubmitSync Performance** (Average time in nanoseconds):

| Operation | Time | vs Baseline |
|-----------|------|-------------|
| `submit()` (baseline) | 287.852 ns | - |
| `submitSyncAccepted` | 255.532 ns | **11% faster** |
| `submitSyncWithBackpressure` | 245.450 ns | **15% faster** |
| `submitSyncRejected` | 25,663.672 ns | ~25.6 μs |

**Key Findings**:
- `submitSync()` has **minimal overhead** when items are accepted
- Backpressure caching **reduces overhead** by ~95%
- Rejection path is fast enough for synchronous checks

### Performance Optimizations

1. **Backpressure Caching**: ~95% reduction in provider calls
2. **Efficient Queue Operations**: Non-blocking operations
3. **Optimized Hot Paths**: Reduced object creation and method calls

## 🔄 Migration Guide

### From 0.0.4 to 0.0.5

**No migration required** - All changes are backward compatible. Existing code continues to work without modification.

### Optional Enhancements

**1. Use `submitSync()` for Immediate Rejection Visibility**:
```java
// Old way (still works)
CompletableFuture<BatchResult<String>> future = batcher.submit("item");

// New way (optional, for immediate rejection visibility)
ItemResult<String> result = batcher.submitSync("item");
if (result instanceof ItemResult.Failure<String> failure) {
    // Handle immediate rejection
}
```

**2. Enable Backpressure Caching** (if using backpressure):
```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .lingerTime(Duration.ofMillis(100))
    .backpressureCacheTtl(Duration.ofMillis(100)) // Optional: customize TTL
    .build();
```

**3. Add OpenTelemetry Tracing** (if using OpenTelemetry):
```java
OpenTelemetryTracingHook otelHook = new OpenTelemetryTracingHook();
BatcherConfig config = BatcherConfig.builder()
    .tracingHook(otelHook)
    .build();
```

## 🐛 Bug Fixes

- **Individual Item Metrics**: Fixed `itemWaitTime` incorrectly recording full latency
  - Now correctly records only queue wait time
  - Full latency recorded separately in `itemSubmitLatency`

## 📚 Documentation

- Enhanced JavaDoc with race condition notes
- Comprehensive examples for new APIs
- Performance benchmark results
- Migration guide (no breaking changes)

## 🧪 Testing

- **Test Coverage**: >90% line coverage, >50% branch coverage
- **Test Count**: 500+ tests across 26+ spec files
- **New Tests**: Comprehensive test suite for all new features
- **Performance Tests**: JMH benchmarks included

## 🔍 Known Limitations

1. **Queue Depth Race Condition**: Documented in JavaDoc - acceptable behavior
   - Small race condition window between queue depth check and `queue.offer()`
   - Handled gracefully with proper error metrics

2. **OpenTelemetry Optional**: Requires OpenTelemetry in classpath for full functionality
   - Gracefully degrades when OpenTelemetry unavailable

3. **Backpressure Cache TTL**: Default 50ms may need tuning for specific use cases
   - Configurable via `BatcherConfig.backpressureCacheTtl()`

## 🙏 Acknowledgments

Thank you to all contributors and users who provided feedback and helped improve Vortex.

## 📖 Resources

- **Documentation**: See README.md and JavaDoc
- **Examples**: See `examples/` directory
- **Benchmarks**: See `build/reports/jmh/html/index.html`
- **Release Readiness Report**: See `documents/releases/RELEASE_0.0.5_READINESS_REPORT.md`

---

**Next Release**: 0.0.6 (planned features: time-windowed metrics, batch size recommendation engine, priority batching support)

