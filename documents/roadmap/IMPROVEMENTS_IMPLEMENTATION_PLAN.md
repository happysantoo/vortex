# Vortex Improvements Implementation Plan

**Date**: January 28, 2026  
**Status**: Phase 1 & Phase 2 Implemented  
**Priority**: High

## Overview

This document outlines the implementation plan for short-term and long-term improvements identified in the workspace review. All implementations will follow the cursorrules principles: **simplicity first**, **readability**, and **maintainability**.

---

## Implementation Principles

Following `.cursor/rules/vortex-rules/RULE.md`:

1. **Simplicity First**: Keep code minimal and easy to understand
2. **Java 21 Features**: Use modern Java features (virtual threads, records, pattern matching)
3. **No Unnecessary Dependencies**: Keep the library lightweight
4. **Clear Naming**: Use descriptive variable and method names
5. **Comprehensive Testing**: >90% line coverage, >80% instruction coverage, >50% branch coverage
6. **Documentation**: All public APIs must have JavaDoc comments
7. **Test-Driven**: Write tests first (TDD approach)

---

## Phase 1: Short-Term Improvements (High Priority)

### 1.1 Batch Processor Thread Resilience ⚠️

**Priority**: Medium  
**Impact**: Medium - Could cause silent failures in production  
**Estimated Effort**: 2-3 days

#### Current Issue
- Single batch processor thread has no restart mechanism
- If thread crashes, processing stops silently
- No monitoring or health checks

#### Implementation Approach

**Design**: Keep it simple - add health monitoring without over-engineering

**Changes Required**:

1. **Add Thread Health Monitoring** (`MicroBatcher.java`)
   - Track batch processor thread state
   - Add metric for thread health status
   - Simple boolean flag to track if processor is running

2. **Add Health Check Method** (`MicroBatcher.java`)
   ```java
   /**
    * Checks if the batch processor thread is healthy and running.
    * 
    * @return true if the batch processor is running, false otherwise
    */
   public boolean isProcessorHealthy()
   ```

3. **Add Metric** (`MetricsManager.java`)
   - Gauge: `vortex.processor.healthy` (1 = healthy, 0 = unhealthy)

4. **Enhance Error Handling** (`MicroBatcher.startBatchProcessor()`)
   - Catch and log thread failures
   - Update health status metric
   - Consider restart mechanism (simple retry with backoff)

**Implementation Steps**:

1. Add `AtomicBoolean processorHealthy` field to `MicroBatcher`
2. Update `startBatchProcessor()` to set health flag
3. Add try-catch in batch processor loop to catch unexpected exceptions
4. Update health flag on exceptions
5. Add `isProcessorHealthy()` public method
6. Add metric gauge for processor health
7. Write comprehensive tests (Spock)
8. Update JavaDoc

**Testing Requirements**:
- Test processor health flag updates correctly
- Test health check returns correct status
- Test metric is recorded correctly
- Test exception handling doesn't crash batcher
- Test health status after thread interruption

**Files to Modify**:
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`
- `src/main/java/com/vajrapulse/vortex/metrics/MetricsManager.java`
- `src/test/groovy/com/vajrapulse/vortex/MicroBatcherLifecycleSpec.groovy` (add tests)

**Documentation**:
- Update JavaDoc for `MicroBatcher`
- Add section to user guide about monitoring processor health

---

### 1.2 Enhanced Backpressure Metrics ⚠️

**Priority**: Medium  
**Impact**: Low-Medium - Better observability  
**Estimated Effort**: 1-2 days

#### Current State
- Basic backpressure metrics exist
- Missing detailed threshold hit rate metrics
- No visibility into rejection patterns

#### Implementation Approach

**Design**: Add simple, focused metrics without complexity

**New Metrics to Add**:

1. **Threshold Hit Rate** (`MetricsManager.java`)
   - Counter: `vortex.backpressure.threshold.hits` - Count of threshold rejections
   - Counter: `vortex.backpressure.full.hits` - Count of full queue rejections
   - Counter: `vortex.backpressure.concurrent.hits` - Count of concurrent batch rejections

2. **Rejection Rate Gauge** (`MetricsManager.java`)
   - Gauge: `vortex.backpressure.rejection.rate` - Current rejection rate (0.0 to 1.0)

3. **Queue Utilization** (`MetricsManager.java`)
   - Gauge: `vortex.queue.utilization` - Current queue utilization (0.0 to 1.0)

**Implementation Steps**:

1. Add counter methods to `MetricsManager` for rejection types
2. Update `SubmissionHandler` to record rejection type metrics
3. Add gauge for rejection rate calculation
4. Add gauge for queue utilization
5. Write comprehensive tests
6. Update JavaDoc

**Testing Requirements**:
- Test all rejection metrics are recorded correctly
- Test rejection rate calculation is accurate
- Test queue utilization calculation is accurate
- Test metrics are recorded at correct times

**Files to Modify**:
- `src/main/java/com/vajrapulse/vortex/metrics/MetricsManager.java`
- `src/main/java/com/vajrapulse/vortex/internal/SubmissionHandler.java`
- `src/test/groovy/com/vajrapulse/vortex/MetricsProviderSpec.groovy` (add tests)

**Documentation**:
- Update metrics documentation
- Add examples of using new metrics

---

### 1.3 Configurable Shutdown Timeouts ⚠️

**Priority**: Low  
**Impact**: Low - Better flexibility  
**Estimated Effort**: 1 day

#### Current State
- Fixed timeouts: 2s queue drain, 5s executor shutdown
- Not configurable per use case

#### Implementation Approach

**Design**: Add simple configuration options without breaking changes

**Changes Required**:

1. **Add to `BatcherConfig.Builder`** (`BatcherConfig.java`)
   ```java
   /**
    * Sets the queue drain timeout for graceful shutdown.
    * 
    * @param timeout the timeout duration (default: 2 seconds)
    * @return this builder instance
    */
   public Builder queueDrainTimeout(Duration timeout)
   
   /**
    * Sets the executor shutdown timeout for graceful shutdown.
    * 
    * @param timeout the timeout duration (default: 5 seconds)
    * @return this builder instance
    */
   public Builder executorShutdownTimeout(Duration timeout)
   ```

2. **Update `ShutdownManager`** (`ShutdownManager.java`)
   - Accept timeout configuration from config
   - Use configured timeouts instead of constants

**Implementation Steps**:

1. Add timeout fields to `BatcherConfig`
2. Add builder methods with defaults (backward compatible)
3. Update `ShutdownManager` constructor to accept timeouts
4. Replace constants with config values
5. Write comprehensive tests
6. Update JavaDoc

**Testing Requirements**:
- Test default timeouts are used when not specified
- Test custom timeouts are respected
- Test shutdown behavior with different timeout values
- Test backward compatibility (existing code works)

**Files to Modify**:
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/internal/ShutdownManager.java`
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java` (pass config to ShutdownManager)
- `src/test/groovy/com/vajrapulse/vortex/MicroBatcherLifecycleSpec.groovy` (add tests)

**Documentation**:
- Update `BatcherConfig` JavaDoc
- Add shutdown timeout examples to user guide

---

## Phase 2: Medium-Term Improvements (Optional)

### 2.1 Early Concurrent Batch Rejection (Configurable) ✅

**Priority**: Low-Medium  
**Impact**: Low-Medium - Better queue utilization  
**Estimated Effort**: 2 days  
**Status**: Implemented (branch: `fix/backpressure-reliability`)

#### Implementation Summary (Completed)
- **BatcherConfig**: Added `earlyConcurrentBatchRejection(boolean)` (default `false`) for backward compatibility.
- **SubmissionHandler**: `evaluateBackpressure()` checks the flag; when enabled, rejects at submit time with `BackpressureStatus.REJECT_CONCURRENT_BATCHES` when `activeBatchCount >= maxConcurrentBatches`. `submitCommon()` handles this by completing the future with `ItemRejectedException.concurrentLimitReached()` and recording `vortex.backpressure.concurrent.hits`.
- **Tests**: `BatcherConfigSpec` validates default and configured state; `MicroBatcherConcurrentDispatchSpec.should reject submissions early when early concurrent rejection is enabled` verifies early rejection and metric.

#### Current State (Pre-Implementation)
- Framework existed but was disabled for backward compatibility
- Concurrent batch limit was checked at dispatch time only
- Items were queued then rejected at dispatch time

#### Implementation Approach

**Design**: Add simple configuration flag to enable/disable early rejection

**Changes Required**:

1. **Add Configuration Option** (`BatcherConfig.java`)
   ```java
   /**
    * Enables early rejection for concurrent batch limits.
    * 
    * <p>When enabled, items are rejected at submission time if adding them
    * would form a batch that cannot be dispatched due to concurrent batch limits.
    * 
    * <p>When disabled (default), items are accepted and rejected at dispatch time.
    * This maintains backward compatibility with existing behavior.
    * 
    * @param enabled true to enable early rejection, false to disable (default)
    * @return this builder instance
    */
   public Builder earlyConcurrentBatchRejection(boolean enabled)
   ```

2. **Enable Framework** (`SubmissionHandler.java`)
   - Uncomment and refine concurrent batch checking logic
   - Only check if configuration is enabled
   - Keep logic simple and conservative

**Implementation Steps**:

1. Add boolean flag to `BatcherConfig` (default: false for backward compatibility)
2. Update `SubmissionHandler.evaluateBackpressure()` to check flag
3. Enable concurrent batch checking when flag is true
4. Refine logic to be conservative (only reject when definitely forming batch)
5. Write comprehensive tests
6. Update JavaDoc and documentation

**Testing Requirements**:
- Test early rejection works when enabled
- Test backward compatibility when disabled
- Test rejection happens at correct time
- Test queue utilization improves when enabled

**Files to Modify**:
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/internal/SubmissionHandler.java`
- `src/test/groovy/com/vajrapulse/vortex/MicroBatcherConcurrentDispatchSpec.groovy` (add tests)

**Documentation**:
- Update configuration documentation
- Add examples showing trade-offs
- Document when to use early rejection

---

## Phase 3: Long-Term Enhancements (Future)

### 3.1 Circuit Breaker Pattern ✅

**Priority**: Low  
**Impact**: Medium - Better resilience  
**Estimated Effort**: 3-4 days  
**Status**: Implemented

#### Implementation Summary (Completed)
- **CircuitBreaker** (`internal/CircuitBreaker.java`): Three-state (CLOSED, OPEN, HALF_OPEN), configurable failure threshold and open duration, optional Micrometer gauge `vortex.circuit.state` and counter `vortex.circuit.open.events`.
- **BatcherConfig**: `circuitBreakerEnabled` (default false), `circuitBreakerFailureThreshold` (default 5), `circuitBreakerOpenDuration` (default 30s).
- **BatchDispatcher**: Optional circuit breaker; check before dispatch and re-check inside executor task; on success/failure call `recordSuccess()`/`recordFailure()`; when open, reject batch with `ItemRejectedException.circuitOpen()`.
- **ItemRejectedException**: Added `circuitOpen()` factory.
- **MicroBatcher.submitAsync**: Uses `handle()` so exceptional batch completion (e.g. circuit open, dispatch rejected) completes with `ItemResult.Failure` instead of completing the future exceptionally.
- **Tests**: CircuitBreakerSpec (unit), BatcherConfigSpec and ItemRejectedExceptionSpec (config/exception), MicroBatcherErrorHandlingSpec (integration).

#### Approach (Original)
- Simple circuit breaker implementation
- No external dependencies
- Configurable failure thresholds
- Integrate with backend dispatch

**Design**: Keep it simple - basic three-state circuit breaker (CLOSED, OPEN, HALF_OPEN)

---

### 3.2 Enhanced Retry Strategies

**Priority**: Low  
**Impact**: Low - Better retry handling  
**Estimated Effort**: 2-3 days

**Status**: Implemented

#### Approach
- Add exponential backoff option
- Configurable retry strategies
- Keep existing simple retry as default

**Design**: Strategy pattern for retry strategies, keep default simple

---

### 3.3 Performance Optimizations

**Priority**: Low  
**Impact**: Low - Performance improvements  
**Estimated Effort**: TBD

**Status**: Benchmarked (no further code optimizations required right now)

#### Approach
- Only if performance issues are identified
- Atomic queue size counter (if needed)
- Profile first, optimize second

**Design**: Measure first, optimize only if needed

---

## Implementation Timeline

### Phase 1 (Short-Term) - 4-6 days
- Week 1: Batch Processor Thread Resilience (2-3 days)
- Week 1-2: Enhanced Backpressure Metrics (1-2 days)
- Week 2: Configurable Shutdown Timeouts (1 day)

### Phase 2 (Medium-Term) - 2 days
- Week 3: Early Concurrent Batch Rejection (2 days)

### Phase 3 (Long-Term) - TBD
- Future: Circuit Breaker, Enhanced Retries, Performance Optimizations

---

## Testing Strategy

For each improvement:

1. **Write Tests First** (TDD)
   - Spock framework (Groovy)
   - >90% line coverage
   - >80% instruction coverage
   - >50% branch coverage

2. **Test Categories**:
   - Happy path scenarios
   - Error conditions
   - Edge cases
   - Concurrent scenarios
   - Backward compatibility

3. **Test Organization**:
   - One spec file per class
   - Use `@Unroll` for parameterized tests
   - Always use `cleanup:` blocks
   - Proper synchronization (CountDownLatch, not Thread.sleep)

---

## Documentation Requirements

For each improvement:

1. **JavaDoc**:
   - Class-level documentation
   - Method-level documentation
   - Parameter documentation
   - Exception documentation
   - Example code where helpful

2. **User Guide Updates**:
   - Add examples
   - Document configuration options
   - Explain use cases

3. **CHANGELOG.md**:
   - Document user-facing changes
   - Note backward compatibility

---

## Code Quality Checklist

Before committing each improvement:

- [ ] All tests pass (`./gradlew test`)
- [ ] Coverage requirements met (`./gradlew jacocoTestCoverageVerification`)
- [ ] No compilation warnings
- [ ] All public APIs have JavaDoc
- [ ] Code follows style guidelines (simplicity first)
- [ ] No unnecessary dependencies
- [ ] Clear naming conventions
- [ ] Proper error handling
- [ ] Thread safety verified
- [ ] Documentation updated

---

## Risk Assessment

### Low Risk
- Enhanced Metrics (additive, no behavior change)
- Configurable Shutdown Timeouts (backward compatible defaults)

### Medium Risk
- Batch Processor Thread Resilience (adds new behavior, needs careful testing)
- Early Concurrent Batch Rejection (changes rejection timing, needs thorough testing)

### Mitigation
- Comprehensive testing for all changes
- Backward compatibility where possible
- Gradual rollout with feature flags
- Monitor metrics after deployment

---

## Success Criteria

### Phase 1 Success
- ✅ Batch processor health monitoring working
- ✅ Enhanced metrics providing useful insights
- ✅ Configurable shutdown timeouts available
- ✅ All tests passing
- ✅ Coverage requirements met
- ✅ Documentation complete

### Phase 2 Success
- ✅ Early concurrent batch rejection configurable
- ✅ Queue utilization improved when enabled
- ✅ Backward compatibility maintained
- ✅ All tests passing
- ✅ Documentation complete

---

## Next Steps

1. **Review and Approve Plan**
   - Review with team
   - Prioritize based on business needs
   - Adjust timeline if needed

2. **Start Phase 1**
   - Begin with Batch Processor Thread Resilience
   - Follow TDD approach
   - Maintain code quality standards

3. **Iterate**
   - Implement one improvement at a time
   - Test thoroughly
   - Document as you go
   - Review before moving to next

---

**Plan Status**: Ready for Implementation  
**Last Updated**: January 28, 2026
