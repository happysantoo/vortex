# Vortex 0.0.4 Backpressure Implementation Plan

**Version**: 0.0.4  
**Status**: Planning  
**Author**: Principal Engineer Review  
**Date**: 2024

## Executive Summary

This document provides a **principal engineer-level critique** of the proposed backpressure design and outlines a **detailed implementation plan** for integrating sophisticated backpressure handling into Vortex Micro-Batching Library version 0.0.4.

### Key Decisions

1. **Implement Phase 1 (Simplified) Design**: ~400 lines of code, DROP and REJECT strategies only
2. **Add Overflow Support for Kafka Use Case**: LifecycleAwareStrategy + OverflowStrategy (~750 additional lines)
3. **Defer ThrottleStrategy to 0.0.5**: Reduces complexity, allows validation of core design
4. **Integrate into MicroBatcher**: No separate class, maintain backward compatibility
5. **Use Existing Metrics Infrastructure**: Leverage current Micrometer integration

### Kafka Consumer Use Case Support

**Requirement**: Support Kafka consumer use case with:
- Overflow to temporary memory when backpressure detected
- Pause Kafka consumer
- Monitor backpressure resolution
- Replay items from overflow
- Resume Kafka consumer

**Solution**: Enhanced design with:
- `LifecycleAwareStrategy` interface for state transitions
- `OverflowStrategy` implementation for overflow handling
- `OverflowStorage` and `ConsumerController` interfaces for pluggability
- Backpressure monitoring in MicroBatcher

**See**: `VORTEX_KAFKA_BACKPRESSURE_USE_CASE_ANALYSIS.md` for detailed analysis

---

## Principal Engineer Design Critique

### 1. Architecture Analysis

#### ✅ **Strengths**

1. **Separation of Concerns**: Excellent separation between detection (`BackpressureProvider`) and handling (`BackpressureStrategy`)
   - Enables independent evolution
   - Promotes composability
   - Follows Single Responsibility Principle

2. **Framework Agnostic**: Pure Java interfaces with no external dependencies
   - Can be used standalone
   - Easy to integrate with any framework
   - No vendor lock-in

3. **Composability**: `CompositeBackpressureProvider` allows combining multiple sources
   - Maximum aggregation (worst-case) is conservative and safe
   - Simple varargs API is intuitive

4. **Backward Compatibility**: Integration into `MicroBatcher` maintains existing API
   - Optional feature (null checks)
   - No breaking changes
   - Gradual adoption path

#### ⚠️ **Concerns & Recommendations**

1. **Thread Safety of Providers**
   - **Issue**: `BackpressureProvider.getBackpressureLevel()` must be thread-safe, but this isn't enforced
   - **Risk**: Concurrent access could cause incorrect readings or exceptions
   - **Recommendation**: 
     - Document thread-safety requirement clearly in JavaDoc
     - Consider adding `@ThreadSafe` annotation
     - Provide thread-safe implementations as examples
     - Add concurrency tests

2. **Performance of Composite Provider**
   - **Issue**: `CompositeBackpressureProvider` calls all providers on every check
   - **Risk**: If providers are expensive (e.g., JMX calls, network requests), this could be slow
   - **Recommendation**:
     - Document that providers should be fast (< 1ms ideally)
     - Consider caching with TTL for expensive providers (Phase 2)
     - Add performance benchmarks

3. **Strategy Execution Context**
   - **Issue**: `BackpressureStrategy.handle()` is called synchronously in `submit()`
   - **Risk**: If strategy blocks (e.g., future ThrottleStrategy), it blocks the submission thread
   - **Recommendation**:
     - Document that strategies should be non-blocking
     - For ThrottleStrategy (Phase 2), use async approach with `CompletableFuture`
     - Consider adding timeout to strategy execution

4. **Error Handling in Strategies**
   - **Issue**: What happens if `BackpressureStrategy.handle()` throws an exception?
   - **Risk**: Unhandled exceptions could crash the submission path
   - **Recommendation**:
     - Wrap strategy execution in try-catch
     - Default to REJECT on strategy failure (fail-safe)
     - Log errors for debugging

5. **Backpressure Level Granularity**
   - **Issue**: 0.0-1.0 scale is good, but threshold comparison is exact (`>= threshold`)
   - **Risk**: Small fluctuations around threshold could cause thrashing
   - **Recommendation**:
     - Consider hysteresis (different thresholds for entering/exiting backpressure)
     - Document threshold selection guidelines
     - Add examples showing threshold tuning

### 2. API Design Critique

#### ✅ **Strengths**

1. **Simple Enum + Record**: `BackpressureResult` using enum + record is clean
   - Easier to understand than sealed interface
   - Pattern matching with switch expressions is elegant
   - Less code than sealed interface approach

2. **Context Object**: `BackpressureContext` provides necessary information
   - Item, level, and provider are sufficient for most strategies
   - Record type is immutable and efficient

3. **Factory Method**: `MicroBatcher.withBackpressure()` is discoverable
   - Clear intent
   - Convenient API
   - Doesn't pollute main constructor

#### ⚠️ **Concerns & Recommendations**

1. **Missing Validation**
   - **Issue**: No validation that `threshold` is in valid range (0.0-1.0)
   - **Risk**: Invalid thresholds could cause unexpected behavior
   - **Recommendation**:
     ```java
     public DropStrategy(double threshold) {
         if (threshold < 0.0 || threshold > 1.0) {
             throw new IllegalArgumentException("Threshold must be in [0.0, 1.0]");
         }
         this.threshold = threshold;
     }
     ```

2. **Strategy Naming for Metrics**
   - **Issue**: No way to identify which strategy is active in metrics
   - **Risk**: Hard to debug which strategy is causing behavior
   - **Recommendation**: Add optional `getStrategyName()` default method (can defer to Phase 2)

3. **Provider Details API**
   - **Issue**: `getDetails()` returns `Map<String, Object>`, but Object values are not type-safe
   - **Risk**: Consumers must cast, potential ClassCastException
   - **Recommendation**: 
     - Document expected value types
     - Consider typed details in Phase 2 (e.g., `Map<String, String>` for simple cases)
     - Keep current design for flexibility

### 3. Integration Critique

#### ✅ **Strengths**

1. **Optional Integration**: Backpressure is optional, doesn't affect existing code
2. **Early Check**: Backpressure checked before queue offer, preventing unnecessary work
3. **Metrics Integration**: Uses existing metrics infrastructure

#### ⚠️ **Concerns & Recommendations**

1. **Double Rejection Path**
   - **Issue**: Current code rejects when queue is full (line 148-151), but backpressure could also reject
   - **Risk**: Two rejection mechanisms could conflict or cause confusion
   - **Recommendation**:
     - **Option A**: Make backpressure check happen BEFORE queue offer, and if backpressure rejects, don't try queue
     - **Option B**: Keep both, but document that backpressure is "early warning" and queue full is "hard limit"
     - **Preferred**: Option A - backpressure is proactive, queue full is reactive

2. **Metrics Duplication**
   - **Issue**: `recordRequestRejected()` is called for both queue full and backpressure rejection
   - **Risk**: Can't distinguish between rejection reasons
   - **Recommendation**:
     - Add `recordBackpressureRejected()` method
     - Or add tags to existing metric: `vortex.requests.rejected{reason="backpressure"}` vs `{reason="queue_full"}`
     - Update metrics to include backpressure-specific counters

3. **Tracing Hook Integration**
   - **Issue**: No tracing hook call for backpressure events
   - **Risk**: Missing observability for backpressure decisions
   - **Recommendation**:
     - Add `onBackpressureRejected()` or `onBackpressureDropped()` to `BatchTracingHook` (optional, can defer)
     - Or extend existing hook with backpressure context

### 4. Testing Strategy Critique

#### ✅ **Strengths**

1. Unit tests for each component
2. Integration tests with MicroBatcher

#### ⚠️ **Concerns & Recommendations**

1. **Concurrency Tests**
   - **Issue**: Need tests for concurrent backpressure checks
   - **Recommendation**: Add tests with multiple threads submitting while backpressure changes

2. **Performance Tests**
   - **Issue**: Need to ensure backpressure check doesn't add significant overhead
   - **Recommendation**: Benchmark `submit()` with and without backpressure

3. **Edge Cases**
   - **Issue**: Need tests for:
     - Provider returns NaN or invalid values
     - Strategy throws exception
     - Provider is null (shouldn't happen, but defensive)
   - **Recommendation**: Add defensive tests

---

## Alternative Design Proposals

### Alternative 1: Event-Driven Backpressure

**Concept**: Instead of checking backpressure on every `submit()`, use an event-driven model where backpressure state changes trigger strategy evaluation.

**Pros**:
- Reduces overhead (no check on every submit)
- Can batch strategy decisions
- More reactive

**Cons**:
- More complex implementation
- Requires state management
- Potential race conditions

**Verdict**: ❌ **Reject** - Too complex for Phase 1, can consider for Phase 2 if performance becomes an issue.

### Alternative 2: Backpressure as Decorator

**Concept**: Create a `BackpressureAwareMicroBatcher` that wraps `MicroBatcher` and adds backpressure logic.

**Pros**:
- Keeps `MicroBatcher` simple
- Clear separation
- Easy to add/remove

**Cons**:
- Duplication of logic
- Two classes to maintain
- Less integrated

**Verdict**: ❌ **Reject** - Integration into `MicroBatcher` is cleaner and more maintainable.

### Alternative 3: Backpressure Callback Instead of Strategy

**Concept**: Instead of strategy pattern, use a callback function that returns boolean (accept/reject).

**Pros**:
- Simpler API
- Less code
- More functional style

**Cons**:
- Less flexible (harder to add DROP vs REJECT distinction)
- Harder to extend
- Less testable

**Verdict**: ❌ **Reject** - Strategy pattern provides better flexibility and extensibility.

### Alternative 4: Backpressure Level as Supplier

**Concept**: Instead of `BackpressureProvider` interface, use `Supplier<Double>` directly.

**Pros**:
- Simpler API
- Less abstraction
- More functional

**Cons**:
- Loses source name and details
- Less composable
- Harder to debug

**Verdict**: ❌ **Reject** - Provider interface provides valuable metadata for observability.

### Alternative 5: Backpressure Threshold in Config

**Concept**: Move threshold to `BatcherConfig` instead of strategy.

**Pros**:
- Centralized configuration
- Single threshold for all strategies

**Cons**:
- Less flexible (can't have different thresholds per strategy)
- Mixes concerns (config vs behavior)

**Verdict**: ❌ **Reject** - Threshold belongs with strategy (different strategies may want different thresholds).

---

## Recommended Design (After Critique)

### Core Principles

1. **Fail-Safe**: If backpressure check fails, default to accepting (don't block system)
2. **Fast**: Backpressure check should be < 1ms
3. **Observable**: All backpressure decisions should be traceable
4. **Thread-Safe**: All components must be thread-safe
5. **Backward Compatible**: Existing code must continue to work

### Modified Integration Flow

```java
public CompletableFuture<BatchResult<T>> submit(T data) {
    if (closed) {
        throw new IllegalStateException("MicroBatcher is closed");
    }
    
    // 1. Check backpressure FIRST (before any other work)
    if (backpressureProvider != null && backpressureStrategy != null) {
        try {
            double backpressure = backpressureProvider.getBackpressureLevel();
            
            // Validate backpressure level
            if (Double.isNaN(backpressure) || backpressure < 0.0 || backpressure > 1.0) {
                logger.warn("Invalid backpressure level: {}, defaulting to 0.0", backpressure);
                backpressure = 0.0;
            }
            
            BackpressureContext<T> context = new BackpressureContext<>(
                item, backpressure, backpressureProvider
            );
            
            BackpressureResult<T> result = backpressureStrategy.handle(context);
            
            return switch (result.action()) {
                case ACCEPT -> {
                    // Continue to normal flow
                    yield proceedWithSubmission(data);
                }
                case REJECT -> {
                    metrics.recordBackpressureRejected(); // New metric
                    CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
                    future.completeExceptionally(
                        new BackpressureException(
                            String.format("Backpressure too high: %.2f (threshold: %.2f, source: %s)",
                                backpressure, getThreshold(context), context.provider().getSourceName()),
                            result.reason()
                        )
                    );
                    yield future;
                }
                case DROP -> {
                    metrics.recordBackpressureDropped(); // New metric
                    // Return success but don't actually process
                    CompletableFuture<BatchResult<T>> future = new CompletableFuture<>();
                    future.complete(new BatchResult<>(List.of(new SuccessEvent<>(data)), List.of()));
                    yield future;
                }
            };
        } catch (Exception e) {
            // Fail-safe: if backpressure check fails, proceed normally
            logger.error("Backpressure check failed, proceeding with submission", e);
            return proceedWithSubmission(data);
        }
    }
    
    // 2. No backpressure - normal flow
    return proceedWithSubmission(data);
}

private CompletableFuture<BatchResult<T>> proceedWithSubmission(T data) {
    // Existing submission logic (tracing, metrics, queue offer, etc.)
    // ...
}
```

### Key Changes from Original Design

1. **Fail-Safe Error Handling**: Wrap strategy execution in try-catch, default to accept
2. **Validation**: Validate backpressure level (NaN, out of range)
3. **Separate Metrics**: `recordBackpressureRejected()` vs `recordRequestRejected()`
4. **Early Check**: Backpressure checked before any other work
5. **Custom Exception**: `BackpressureException` instead of generic `Exception`

---

## Implementation Plan

### Phase 1: Core Interfaces and Types

**Files to Create**:
1. `src/main/java/com/vajrapulse/vortex/backpressure/BackpressureProvider.java`
2. `src/main/java/com/vajrapulse/vortex/backpressure/BackpressureStrategy.java`
3. `src/main/java/com/vajrapulse/vortex/backpressure/BackpressureContext.java`
4. `src/main/java/com/vajrapulse/vortex/backpressure/BackpressureResult.java`
5. `src/main/java/com/vajrapulse/vortex/backpressure/BackpressureException.java`
6. `src/main/java/com/vajrapulse/vortex/backpressure/LifecycleAwareStrategy.java` (NEW)
7. `src/main/java/com/vajrapulse/vortex/backpressure/OverflowStorage.java` (NEW)
8. `src/main/java/com/vajrapulse/vortex/backpressure/ConsumerController.java` (NEW)

**Estimated Lines**: ~250 lines (was ~150, +100 for overflow support)

**Tasks**:
- [ ] Define `BackpressureProvider` interface with JavaDoc
- [ ] Define `BackpressureStrategy` interface
- [ ] Create `BackpressureContext` record
- [ ] Create `BackpressureResult` enum + record
- [ ] Create `BackpressureException` class
- [ ] **Define `LifecycleAwareStrategy` interface (NEW)**
- [ ] **Define `OverflowStorage` interface (NEW)**
- [ ] Add validation and thread-safety documentation

### Phase 2: Built-in Providers

**Files to Create**:
1. `src/main/java/com/vajrapulse/vortex/backpressure/QueueDepthBackpressureProvider.java`
2. `src/main/java/com/vajrapulse/vortex/backpressure/CompositeBackpressureProvider.java`

**Estimated Lines**: ~120 lines

**Tasks**:
- [ ] Implement `QueueDepthBackpressureProvider` with linear scaling
- [ ] Add validation (maxCapacity > 0)
- [ ] Implement `CompositeBackpressureProvider` with varargs constructor
- [ ] Add validation (at least one provider)
- [ ] Implement maximum aggregation logic
- [ ] Add unit tests

### Phase 3: Built-in Strategies

**Files to Create**:
1. `src/main/java/com/vajrapulse/vortex/backpressure/DropStrategy.java`
2. `src/main/java/com/vajrapulse/vortex/backpressure/RejectStrategy.java`
3. `src/main/java/com/vajrapulse/vortex/backpressure/OverflowStrategy.java` (NEW)

**Estimated Lines**: ~380 lines (was ~80, +300 for OverflowStrategy)

**Tasks**:
- [ ] Implement `DropStrategy` with threshold validation
- [ ] Implement `RejectStrategy` with threshold validation
- [ ] **Implement `OverflowStrategy` with lifecycle support (NEW)**
- [ ] Add `BackpressureException` creation with context
- [ ] Add unit tests

### Phase 4: MicroBatcher Integration

**Files to Modify**:
1. `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`
2. `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
3. `src/main/java/com/vajrapulse/vortex/MetricsManager.java`

**Estimated Lines**: ~200 lines (was ~100, +100 for lifecycle monitoring)

**Tasks**:
- [ ] Add `backpressureProvider` and `backpressureStrategy` fields to `MicroBatcher`
- [ ] Add constructor overload with backpressure parameters
- [ ] Add `withBackpressure()` factory method
- [ ] Integrate backpressure check in `submit()` method (early, before queue offer)
- [ ] Add fail-safe error handling
- [ ] Add backpressure validation (NaN, range checks)
- [ ] **Add backpressure state tracking (active/inactive) (NEW)**
- [ ] **Add ScheduledExecutorService for backpressure monitoring (NEW)**
- [ ] **Add lifecycle callback support (onBackpressureEntered/Resolved/Active) (NEW)**
- [ ] Add `backpressureProvider()` and `backpressureStrategy()` to `BatcherConfig.Builder`
- [ ] Add `recordBackpressureRejected()` and `recordBackpressureDropped()` to `MetricsManager`
- [ ] Update `MetricsProvider` to include backpressure metrics

### Phase 5: Testing

**Files to Create**:
1. `src/test/groovy/com/vajrapulse/vortex/backpressure/BackpressureProviderSpec.groovy`
2. `src/test/groovy/com/vajrapulse/vortex/backpressure/BackpressureStrategySpec.groovy`
3. `src/test/groovy/com/vajrapulse/vortex/backpressure/QueueDepthBackpressureProviderSpec.groovy`
4. `src/test/groovy/com/vajrapulse/vortex/backpressure/CompositeBackpressureProviderSpec.groovy`
5. `src/test/groovy/com/vajrapulse/vortex/backpressure/DropStrategySpec.groovy`
6. `src/test/groovy/com/vajrapulse/vortex/backpressure/RejectStrategySpec.groovy`
7. `src/test/groovy/com/vajrapulse/vortex/MicroBatcherBackpressureSpec.groovy`

**Estimated Lines**: ~600 lines

**Tasks**:
- [ ] Unit tests for all interfaces and implementations
- [ ] Integration tests with `MicroBatcher`
- [ ] Concurrency tests
- [ ] Edge case tests (NaN, invalid values, exceptions)
- [ ] Performance tests
- [ ] Backward compatibility tests

### Phase 6: Documentation and Examples

**Files to Create/Modify**:
1. `examples/BackpressureIntegrationExample.java` (new, comprehensive)
2. `examples/KafkaOverflowExample.java` (new, Kafka use case)
3. `examples/InMemoryOverflowStorage.java` (new, example implementation)
4. `examples/KafkaConsumerController.java` (new, example implementation)
5. `documents/guides/BACKPRESSURE_ADVANCED_GUIDE.md` (new)
6. `README.md` (update)
7. `CHANGELOG.md` (update)

**Estimated Lines**: ~700 lines (was ~400, +300 for Kafka examples)

**Tasks**:
- [ ] Create comprehensive example showing all features
- [ ] **Create Kafka overflow example (NEW)**
- [ ] **Create InMemoryOverflowStorage example (NEW)**
- [ ] Update README with backpressure section
- [ ] Create advanced guide with best practices
- [ ] Update CHANGELOG
- [ ] Add JavaDoc to all public APIs

### Phase 7: Metrics and Observability

**Files to Modify**:
1. `src/main/java/com/vajrapulse/vortex/MetricsManager.java`
2. `src/main/java/com/vajrapulse/vortex/MetricsProvider.java`

**Tasks**:
- [ ] Add `vortex.backpressure.rejected` counter
- [ ] Add `vortex.backpressure.dropped` counter
- [ ] Add `vortex.backpressure.level` gauge (current backpressure level)
- [ ] Update `MetricsProvider` interface
- [ ] Add tests for metrics

---

## Implementation Checklist

### Core Components
- [ ] `BackpressureProvider` interface
- [ ] `BackpressureStrategy` interface
- [ ] `BackpressureContext` record
- [ ] `BackpressureResult` enum + record
- [ ] `BackpressureException` class
- [ ] `LifecycleAwareStrategy` interface (NEW)
- [ ] `OverflowStorage` interface (NEW)

### Built-in Providers
- [ ] `QueueDepthBackpressureProvider` (linear scaling)
- [ ] `CompositeBackpressureProvider` (varargs, max aggregation)

### Built-in Strategies
- [ ] `DropStrategy` (threshold validation)
- [ ] `RejectStrategy` (threshold validation, custom exception)
- [ ] `OverflowStrategy` (lifecycle-aware, overflow support) (NEW)

### Integration
- [ ] `MicroBatcher` constructor overload
- [ ] `MicroBatcher.withBackpressure()` factory method
- [ ] `submit()` method integration (early check, fail-safe)
- [ ] Backpressure state tracking (active/inactive) (NEW)
- [ ] Backpressure monitoring (ScheduledExecutorService) (NEW)
- [ ] Lifecycle callback support (NEW)
- [ ] `BatcherConfig.Builder` methods
- [ ] Metrics integration

### Testing
- [ ] Unit tests (all components)
- [ ] Integration tests
- [ ] Concurrency tests
- [ ] Edge case tests
- [ ] Performance tests
- [ ] Backward compatibility tests

### Documentation
- [ ] JavaDoc (all public APIs)
- [ ] README update
- [ ] Advanced guide
- [ ] Examples
- [ ] CHANGELOG

### Metrics
- [ ] `vortex.backpressure.rejected` counter
- [ ] `vortex.backpressure.dropped` counter
- [ ] `vortex.backpressure.level` gauge
- [ ] `MetricsProvider` updates

---

## Risk Assessment

### High Risk
1. **Thread Safety**: Providers must be thread-safe
   - **Mitigation**: Clear documentation, concurrency tests, thread-safe examples

2. **Performance Impact**: Backpressure check on every submit
   - **Mitigation**: Benchmark, optimize providers, consider caching (Phase 2)

3. **Error Handling**: Strategy exceptions could break submission
   - **Mitigation**: Fail-safe error handling, comprehensive tests

### Medium Risk
1. **API Complexity**: More configuration options
   - **Mitigation**: Sensible defaults, clear documentation, examples

2. **Backward Compatibility**: Must not break existing code
   - **Mitigation**: Optional feature, comprehensive compatibility tests

### Low Risk
1. **Metrics Overhead**: Additional metrics
   - **Mitigation**: Use existing infrastructure, optional metrics

---

## Success Criteria

1. ✅ All tests pass (>90% coverage)
2. ✅ No performance regression (< 5% overhead)
3. ✅ Backward compatible (existing code works unchanged)
4. ✅ Documentation complete
5. ✅ Examples work
6. ✅ Metrics integrated
7. ✅ Code review approved

---

## Timeline Estimate

- **Phase 1-3** (Core interfaces, providers, strategies): 3-4 days (was 2-3, +1 for overflow)
- **Phase 4** (Integration): 3-4 days (was 2-3, +1 for lifecycle monitoring)
- **Phase 5** (Testing): 4-5 days (was 3-4, +1 for overflow tests)
- **Phase 6** (Documentation): 2-3 days (was 1-2, +1 for Kafka examples)
- **Phase 7** (Metrics): 1 day

**Total**: ~13-17 days (was ~10-13, +3-4 for overflow support)

---

## Next Steps

1. Review and approve this plan
2. Create feature branch `feature/0.0.4-backpressure`
3. Implement Phase 1 (Core interfaces)
4. Implement Phase 2 (Providers)
5. Implement Phase 3 (Strategies)
6. Implement Phase 4 (Integration)
7. Implement Phase 5 (Testing)
8. Implement Phase 6 (Documentation)
9. Implement Phase 7 (Metrics)
10. Code review
11. Merge to main
12. Release 0.0.4

---

## Appendix: Code Structure

```
src/main/java/com/vajrapulse/vortex/
├── MicroBatcher.java (modified)
├── BatcherConfig.java (modified)
├── MetricsManager.java (modified)
├── MetricsProvider.java (modified)
└── backpressure/
    ├── BackpressureProvider.java (new)
    ├── BackpressureStrategy.java (new)
    ├── LifecycleAwareStrategy.java (new)
    ├── BackpressureContext.java (new)
    ├── BackpressureResult.java (new)
    ├── BackpressureException.java (new)
    ├── OverflowStorage.java (new)
    ├── QueueDepthBackpressureProvider.java (new)
    ├── CompositeBackpressureProvider.java (new)
    ├── DropStrategy.java (new)
    ├── RejectStrategy.java (new)
    └── OverflowStrategy.java (new)

src/test/groovy/com/vajrapulse/vortex/
├── MicroBatcherSpec.groovy (modified)
└── backpressure/
    ├── BackpressureProviderSpec.groovy (new)
    ├── BackpressureStrategySpec.groovy (new)
    ├── QueueDepthBackpressureProviderSpec.groovy (new)
    ├── CompositeBackpressureProviderSpec.groovy (new)
    ├── DropStrategySpec.groovy (new)
    ├── RejectStrategySpec.groovy (new)
    ├── OverflowStrategySpec.groovy (new)
    └── MicroBatcherBackpressureSpec.groovy (new)
```

---

## Conclusion

This plan provides a comprehensive, principal engineer-reviewed approach to implementing backpressure in Vortex 0.0.4. The design is simplified, maintainable, and follows best practices while addressing all identified concerns and risks.

The implementation will be done incrementally, with thorough testing at each phase, ensuring a high-quality, production-ready feature.

