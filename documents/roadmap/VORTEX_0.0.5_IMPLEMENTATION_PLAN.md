# Vortex 0.0.5 Features and Implementation Plan

**Version**: 0.0.5  
**Status**: Planning  
**Target Release**: TBD  
**Estimated Duration**: 2-3 weeks

---

## Executive Summary

Version 0.0.5 focuses on **developer experience improvements** and **ease of adoption**. The release introduces factory methods for common patterns, health check utilities, batch size presets, and prepares the foundation for adaptive batching in future releases.

### Key Goals
1. ✅ **Reduce boilerplate** - Factory methods for common use cases
2. ✅ **Improve observability** - Built-in health check utilities
3. ✅ **Simplify configuration** - Batch size presets
4. ✅ **Better developer experience** - Self-documenting APIs

---

## Features Overview

### ✅ Phase 1: Quick Wins (COMPLETED)

#### 1. Factory Methods for Common Patterns
**Status**: ✅ **COMPLETED**

Added factory methods to `MicroBatcher` for common use cases:

- `MicroBatcher.forHighThroughput()` - Large batches (100), longer linger (500ms)
- `MicroBatcher.forLowLatency()` - Small batches (5), short linger (10ms)
- `MicroBatcher.forBalanced()` - Medium batches (20), medium linger (100ms)
- `MicroBatcher.forResilient()` - With retry support (3 retries, 100ms delay)

**Benefits**:
- Zero configuration for common patterns
- Best practices built-in
- Self-documenting API
- Can still customize via builder

**Files Added/Modified**:
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java` - Added factory methods
- `src/test/groovy/com/vajrapulse/vortex/MicroBatcherFactoryMethodsSpec.groovy` - Tests

#### 2. Health Check Utilities
**Status**: ✅ **COMPLETED**

Added `BatcherHealth` utility class for standardized health checks:

- `BatcherHealth.check()` - Default thresholds (50% failure = DOWN, 10% = DEGRADED)
- `BatcherHealth.checkWithThresholds()` - Custom thresholds
- `BatcherHealth.getHealthInfo()` - Detailed health information

**Benefits**:
- Standardized health checks across applications
- Easy integration with Spring Actuator, Kubernetes probes
- Consistent health reporting

**Files Added/Modified**:
- `src/main/java/com/vajrapulse/vortex/BatcherHealth.java` - New utility class
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java` - Added `getConfig()` method
- `src/test/groovy/com/vajrapulse/vortex/BatcherHealthSpec.groovy` - Tests

#### 3. Batch Size Presets
**Status**: ✅ **COMPLETED**

Added `BatchSizePreset` enum with predefined configurations:

- `TINY` - 5 items, 10ms linger (ultra-low latency)
- `SMALL` - 10 items, 50ms linger (low latency)
- `MEDIUM` - 20 items, 100ms linger (balanced, default)
- `LARGE` - 50 items, 200ms linger (high throughput)
- `HUGE` - 100 items, 500ms linger (maximum throughput)

**Benefits**:
- Quick start for common scenarios
- Self-documenting preset names
- Can customize further via builder

**Files Added/Modified**:
- `src/main/java/com/vajrapulse/vortex/BatchSizePreset.java` - New enum
- `src/test/groovy/com/vajrapulse/vortex/BatchSizePresetSpec.groovy` - Tests

---

## Phase 2: High-Value Features (PLANNED)

### 1. Built-in Adaptive Batch Sizing ⭐⭐⭐⭐⭐

**Priority**: HIGH  
**Effort**: Medium (2-3 days)  
**Value**: Very High

#### Problem
Users must manually implement adaptive batching using `MetricsProvider` and `updateBatchSize()`, leading to boilerplate and inconsistent implementations.

#### Solution

Create an `AdaptiveMicroBatcher` wrapper that automatically adjusts batch size based on:
- Failure rates
- Queue depth
- Latency metrics

#### Design

```java
public class AdaptiveMicroBatcher<T> implements AutoCloseable {
    private final MicroBatcher<T> delegate;
    private final AdaptiveConfig adaptiveConfig;
    private final ScheduledExecutorService scheduler;
    
    public static <T> AdaptiveMicroBatcher<T> create(
            Backend<T> backend,
            BatcherConfig baseConfig,
            AdaptiveConfig adaptiveConfig,
            MeterRegistry registry) {
        // Wraps MicroBatcher and adds adaptive behavior
    }
    
    // Delegates all methods to underlying MicroBatcher
    // Adds automatic batch size adjustment
}
```

#### Configuration

```java
AdaptiveConfig adaptive = AdaptiveConfig.builder()
    .minBatchSize(5)
    .maxBatchSize(50)
    .initialBatchSize(10)
    .adjustmentInterval(Duration.ofSeconds(10))
    .failureRateThreshold(0.1)      // Reduce if > 10%
    .successRateThreshold(0.99)     // Increase if > 99%
    .queueDepthThreshold(0.8)       // Reduce if queue > 80%
    .adjustmentStep(2)               // Change by 2 at a time
    .build();
```

#### Implementation Steps

1. **Create `AdaptiveConfig` class** (1 day)
   - Builder pattern
   - Validation
   - Sensible defaults

2. **Create `AdaptiveMicroBatcher` wrapper** (1 day)
   - Wraps `MicroBatcher`
   - Implements `AutoCloseable`
   - Delegates all methods
   - Adds monitoring thread

3. **Implement adaptive logic** (1 day)
   - Monitor metrics periodically
   - Calculate adjustments
   - Apply batch size changes
   - Log adjustments (debug mode)

4. **Add tests** (0.5 day)
   - Test adjustment logic
   - Test bounds (min/max)
   - Test different scenarios

5. **Documentation** (0.5 day)
   - Usage examples
   - Configuration guide
   - Best practices

**Files to Create**:
- `src/main/java/com/vajrapulse/vortex/AdaptiveMicroBatcher.java`
- `src/main/java/com/vajrapulse/vortex/AdaptiveConfig.java`
- `src/test/groovy/com/vajrapulse/vortex/AdaptiveMicroBatcherSpec.groovy`
- `examples/AdaptiveBatchingExample.java` (update existing)

#### Testing Strategy

- Unit tests for adjustment logic
- Integration tests with mock backends
- Performance tests to ensure low overhead
- Edge case tests (min/max bounds, rapid changes)

---

### 2. Enhanced Testing Utilities ⭐⭐⭐

**Priority**: MEDIUM  
**Effort**: Low-Medium (1-2 days)  
**Value**: Medium

#### Problem
Basic test utilities exist, but could be more comprehensive for common testing scenarios.

#### Solution

Expand `MicroBatcherTestUtils` with:

```java
public class MicroBatcherTestUtils {
    /**
     * Creates a batcher with a mock backend that can be controlled in tests.
     */
    public static <T> TestMicroBatcher<T> createTestBatcher();
    
    /**
     * Waits for a specific number of batches to be dispatched.
     */
    public static <T> void waitForBatches(
            MicroBatcher<T> batcher,
            int expectedBatches,
            Duration timeout);
    
    /**
     * Asserts that metrics meet certain conditions.
     */
    public static <T> void assertMetrics(
            MicroBatcher<T> batcher,
            Consumer<MetricsProvider> assertions);
    
    /**
     * Creates a backend that simulates failures at a given rate.
     */
    public static <T> Backend<T> createFailingBackend(double failureRate);
    
    /**
     * Creates a backend that introduces latency.
     */
    public static <T> Backend<T> createSlowBackend(Duration latency);
}
```

**Implementation Steps**:
1. Create `TestMicroBatcher` wrapper (0.5 day)
2. Add wait utilities (0.5 day)
3. Add assertion helpers (0.5 day)
4. Add backend factories (0.5 day)
5. Add tests (0.5 day)

**Files to Modify**:
- `src/test/java/com/vajrapulse/vortex/MicroBatcherTestUtils.java` - Expand existing

---

## Phase 3: Nice-to-Have Features (FUTURE)

### 1. Time-Windowed Metrics ⭐⭐⭐

**Priority**: LOW  
**Effort**: Medium (2-3 days)  
**Value**: Medium

Add time-windowed metrics to `MetricsProvider`:

```java
public interface MetricsProvider {
    // Existing cumulative methods...
    
    /**
     * Returns the failure rate in the last N minutes.
     */
    double getFailureRateLastNMinutes(int minutes);
    
    /**
     * Returns the average queue depth in the last N minutes.
     */
    double getAverageQueueDepthLastNMinutes(int minutes);
    
    /**
     * Returns the throughput (items/second) in the last N minutes.
     */
    double getThroughputLastNMinutes(int minutes);
}
```

**Implementation**: Use Micrometer's `TimeWindowCounter` or similar.

**Deferred to**: Future release (0.0.6+)

---

### 2. Batch Size Recommendation Engine ⭐⭐

**Priority**: LOW  
**Effort**: Medium-High (3-4 days)  
**Value**: Low-Medium

Utility that analyzes metrics and recommends optimal batch sizes:

```java
public class BatchSizeRecommender {
    public static Recommendation recommend(
            MetricsProvider metrics,
            BatcherDiagnostics diagnostics,
            Duration targetLatency);
}
```

**Deferred to**: Future release (0.0.6+)

---

## Implementation Timeline

### Week 1: Quick Wins ✅ COMPLETED
- ✅ Day 1-2: Factory methods
- ✅ Day 3: Health check utilities
- ✅ Day 4: Batch size presets
- ✅ Day 5: Tests and documentation

### Week 2: Adaptive Batching (PLANNED)
- Day 1-2: `AdaptiveConfig` and `AdaptiveMicroBatcher` classes
- Day 3: Adaptive logic implementation
- Day 4: Tests
- Day 5: Documentation and examples

### Week 3: Testing Utilities & Polish (PLANNED)
- Day 1-2: Enhanced testing utilities
- Day 3: Integration tests
- Day 4: Documentation updates
- Day 5: Release preparation

---

## Testing Strategy

### Unit Tests
- ✅ Factory methods create correct configurations
- ✅ Health check utilities return correct status
- ✅ Batch size presets have correct values
- ⏳ Adaptive batching adjusts correctly
- ⏳ Testing utilities work as expected

### Integration Tests
- ✅ Factory methods work with real backends
- ✅ Health checks work with various scenarios
- ⏳ Adaptive batching with real workloads
- ⏳ Testing utilities in real test scenarios

### Performance Tests
- ⏳ Adaptive batching overhead (< 1% CPU)
- ⏳ Health check performance (< 1ms)

---

## Documentation Updates

### README Updates
- [ ] Add factory methods section
- [ ] Add health check examples
- [ ] Add batch size presets examples
- [ ] Update quick start guide

### New Documentation
- [ ] `documents/guides/ADAPTIVE_BATCHING.md` - Adaptive batching guide
- [ ] `documents/guides/HEALTH_CHECKS.md` - Health check integration guide
- [ ] `examples/HealthCheckExample.java` - Health check example
- [ ] `examples/FactoryMethodsExample.java` - Factory methods example

---

## Breaking Changes

**None** - All changes are backward compatible.

---

## Migration Guide

### From 0.0.4 to 0.0.5

No migration required. All existing code continues to work.

**Optional**: Consider using factory methods for new code:

```java
// Old way (still works)
BatcherConfig config = BatcherConfig.builder()
    .batchSize(20)
    .lingerTime(Duration.ofMillis(100))
    .build();
MicroBatcher<String> batcher = new MicroBatcher<>(backend, config);

// New way (optional, easier)
MicroBatcher<String> batcher = MicroBatcher.forBalanced(backend, registry);
```

---

## Release Checklist

### Code
- [x] Factory methods implemented and tested
- [x] Health check utilities implemented and tested
- [x] Batch size presets implemented and tested
- [ ] Adaptive batching implemented and tested
- [ ] Enhanced testing utilities implemented and tested
- [ ] All tests passing
- [ ] Code coverage > 90%

### Documentation
- [ ] README updated
- [ ] JavaDoc complete
- [ ] Examples updated
- [ ] Migration guide written

### Release
- [ ] Version bumped to 0.0.5
- [ ] CHANGELOG updated
- [ ] Release notes prepared
- [ ] Tagged and published

---

## Success Metrics

### Developer Experience
- **Reduced boilerplate**: Factory methods eliminate 5-10 lines of config code
- **Faster onboarding**: New users can start in < 5 minutes
- **Better defaults**: Presets provide optimized configurations

### Adoption
- **Health check integration**: Easy integration with monitoring systems
- **Adaptive batching**: Automatic optimization reduces manual tuning

### Quality
- **Test coverage**: Maintain > 90% coverage
- **Performance**: No regression in throughput/latency
- **Backward compatibility**: 100% compatible with 0.0.4

---

## Future Considerations (Post-0.0.5)

### 0.0.6 Potential Features
1. Time-windowed metrics
2. Batch size recommendation engine
3. Priority batching support
4. Circuit breaker integration
5. Rate limiting support

### Long-term Vision
1. **Auto-tuning**: Fully automatic batch size optimization
2. **ML-based optimization**: Machine learning for optimal configurations
3. **Distributed batching**: Multi-instance coordination
4. **Advanced observability**: Built-in dashboards and alerts

---

## Conclusion

Version 0.0.5 focuses on **developer experience** and **ease of adoption**. The completed quick wins (factory methods, health checks, presets) provide immediate value, while the planned adaptive batching feature will significantly reduce manual tuning effort.

**Next Steps**:
1. ✅ Complete quick wins (DONE)
2. ⏳ Implement adaptive batching
3. ⏳ Add enhanced testing utilities
4. ⏳ Prepare release

**Estimated Completion**: 2-3 weeks from start

