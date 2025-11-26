# Release 0.0.2 Implementation Plan

## Overview

This document outlines the implementation plan for Release 0.0.2, which includes all improvements suggested in `VORTEX_LIBRARY_IMPROVEMENTS.md`. The plan is organized by priority and includes task breakdown, dependencies, and testing requirements.

## Release Goals

- Enhance API usability with better result tracking and callbacks
- Improve error handling and debugging capabilities
- Expand metrics and observability
- Add runtime configuration capabilities
- Improve testing and documentation
- Add Spring Boot integration

## Implementation Phases

### Phase 1: Foundation (High Priority)
**Goal:** Address immediate pain points and improve core API usability

#### Task 1.1: Sealed ItemResult Type
**Priority:** High  
**Estimated Effort:** 2 hours  
**Dependencies:** None

**Implementation:**
- Create `ItemResult<T>` sealed interface in `src/main/java/com/vajrapulse/vortex/ItemResult.java`
- Implement `ItemResult.Success<T>` and `ItemResult.Failure<T>` records
- Add factory methods: `ItemResult.success(T item)` and `ItemResult.failure(T item, Throwable error)`
- Add JavaDoc documentation

**Files to Create:**
- `src/main/java/com/vajrapulse/vortex/ItemResult.java`

**Files to Modify:**
- None (new type)

**Tests:**
- `src/test/groovy/com/vajrapulse/vortex/ItemResultSpec.groovy` - Test sealed interface, records, and factory methods

**Acceptance Criteria:**
- Sealed interface compiles and works with pattern matching
- Factory methods create correct instances
- Records properly implement interface
- 100% test coverage

---

#### Task 1.2: Item Result Tracking in BatchResult
**Priority:** High  
**Estimated Effort:** 3 hours  
**Dependencies:** Task 1.1 (ItemResult type)

**Implementation:**
- Add `findItemResult(T item, BiPredicate<T, T> equalityComparator)` method to `BatchResult`
- Add overloaded method `findItemResult(T item)` that uses `Objects::equals`
- Update JavaDoc

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatchResult.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/BatchResultSpec.groovy`
- Test finding items in successes
- Test finding items in failures
- Test with custom equality comparator
- Test with null items
- Test when item not found

**Acceptance Criteria:**
- Method finds items in both successes and failures
- Custom comparator works correctly
- Returns `Optional.empty()` when item not found
- Handles null items gracefully
- 100% test coverage

---

#### Task 1.3: Error Handling Improvements in BatchResult
**Priority:** High  
**Estimated Effort:** 2 hours  
**Dependencies:** None

**Implementation:**
- Add `isCompleteSuccess()` method (alias for existing `isAllSuccess()` for consistency)
- Add `isCompleteFailure()` method
- Add `getFailureRate()` method
- Add `getFailuresByType()` method
- Update JavaDoc

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatchResult.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/BatchResultSpec.groovy`
- Test all new methods with various scenarios
- Test edge cases (empty batch, all success, all failure, mixed)

**Acceptance Criteria:**
- All methods work correctly
- Edge cases handled properly
- 100% test coverage

---

#### Task 1.4: Queue Wait Time Metrics
**Priority:** High  
**Estimated Effort:** 3 hours  
**Dependencies:** None

**Implementation:**
- Track queue wait time per request in `PendingRequest`
- Add histogram metric `vortex.queue.wait.time` in `MicroBatcher`
- Add percentile metrics: `vortex.queue.wait.time.p50`, `.p95`, `.p99`
- Update metrics registration in constructor

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`
- `src/main/java/com/vajrapulse/vortex/PendingRequest.java` (if needed for tracking)

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Verify metrics are recorded correctly
- Test with various wait times

**Acceptance Criteria:**
- Metrics are recorded for all requests
- Percentiles calculated correctly
- Metrics visible in MeterRegistry
- 100% test coverage

---

### Phase 2: Enhanced Features (Medium Priority)
**Goal:** Add advanced features for better usability and observability

#### Task 2.1: Batch Completion Callbacks
**Priority:** Medium  
**Estimated Effort:** 4 hours  
**Dependencies:** Task 1.1, Task 1.2 (ItemResult and findItemResult)

**Implementation:**
- Add `submitWithCallback(T item, BiConsumer<T, ItemResult<T>> callback)` method to `MicroBatcher`
- Method should submit item and register callback
- Callback executes when batch completes with item's result
- Return `CompletableFuture<Void>` that completes after callback

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Add tests to `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test callback execution on success
- Test callback execution on failure
- Test callback with multiple items in batch
- Test callback exception handling

**Acceptance Criteria:**
- Callback executes when batch completes
- Correct ItemResult passed to callback
- Exceptions in callback don't break batcher
- 100% test coverage

---

#### Task 2.2: Per-Item Metrics
**Priority:** Medium  
**Estimated Effort:** 5 hours  
**Dependencies:** None

**Implementation:**
- Add `perItemMetrics` flag to `BatcherConfig.Builder`
- Add per-item metrics tracking in `MicroBatcher`:
  - `vortex.item.submit.latency` - Time from submit to batch completion
  - `vortex.item.wait.time` - Time item waits in queue
  - `vortex.item.batch.size` - Size of batch when item was processed
- Only track if `perItemMetrics` is enabled (performance consideration)

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test metrics when enabled
- Test metrics when disabled (should not be created)
- Verify metric values are correct

**Acceptance Criteria:**
- Metrics only created when enabled
- Metrics track correct values
- No performance impact when disabled
- 100% test coverage

---

#### Task 2.3: Batch Size Distribution Metrics
**Priority:** Medium  
**Estimated Effort:** 3 hours  
**Dependencies:** None

**Implementation:**
- Add histogram metric `vortex.batch.size` in `MicroBatcher`
- Add summary statistics: `vortex.batch.size.avg`, `.min`, `.max`
- Record batch size on every dispatch

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test with various batch sizes
- Verify statistics are correct

**Acceptance Criteria:**
- Metrics recorded for all batches
- Statistics calculated correctly
- 100% test coverage

---

#### Task 2.4: Test Utilities
**Priority:** Medium  
**Estimated Effort:** 4 hours  
**Dependencies:** None

**Implementation:**
- Create `MicroBatcherTestUtils` class in `src/test/java/com/vajrapulse/vortex/MicroBatcherTestUtils.java`
- Add `createTestBackend()` method that returns a `TestBackend<T>`
- Add `waitForBatches(MicroBatcher<T> batcher)` method
- Create `TestBackend<T>` class that records all batches
- Add helper methods for common test scenarios

**Files to Create:**
- `src/test/java/com/vajrapulse/vortex/MicroBatcherTestUtils.java`
- `src/test/java/com/vajrapulse/vortex/TestBackend.java`

**Tests:**
- Test utilities themselves in `src/test/groovy/com/vajrapulse/vortex/MicroBatcherTestUtilsSpec.groovy`
- Update existing tests to use utilities where appropriate

**Acceptance Criteria:**
- Utilities make testing easier
- TestBackend records all batches correctly
- waitForBatches works reliably
- 100% test coverage

---

### Phase 3: Advanced Features (Low Priority)
**Goal:** Add runtime flexibility and framework integration

#### Task 3.1: Debug Mode
**Priority:** Low  
**Estimated Effort:** 4 hours  
**Dependencies:** None

**Implementation:**
- Add `debugMode` flag to `BatcherConfig.Builder`
- Add debug logging in `MicroBatcher`:
  - Batch formation events
  - Item submission events
  - Batch dispatch events
  - Queue depth changes
  - Timing information
- Use SLF4J for logging (add dependency if needed)

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`
- `build.gradle.kts` (add SLF4J dependency if needed)

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test that debug logging occurs when enabled
- Test that no logging occurs when disabled

**Acceptance Criteria:**
- Debug logging works correctly
- No performance impact when disabled
- Logs are informative and useful
- 100% test coverage

---

#### Task 3.2: Retry Support
**Priority:** Low  
**Estimated Effort:** 6 hours  
**Dependencies:** None

**Implementation:**
- Add retry configuration to `BatcherConfig.Builder`:
  - `maxRetries` (int, default 0)
  - `retryDelay` (Duration, default ZERO)
  - `retryableErrorPredicate` (Predicate<Throwable>, default always false)
- Implement retry logic in `MicroBatcher.processBatchResults()`
- Retry failed items that match predicate
- Respect maxRetries and retryDelay

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test retry with retryable errors
- Test retry with non-retryable errors
- Test maxRetries limit
- Test retryDelay
- Test retryableErrorPredicate

**Acceptance Criteria:**
- Retries work correctly
- Respects maxRetries limit
- Respects retryDelay
- Only retries retryable errors
- 100% test coverage

---

#### Task 3.3: Dynamic Configuration
**Priority:** Low  
**Estimated Effort:** 6 hours  
**Dependencies:** None

**Implementation:**
- Make `BatcherConfig` mutable or create `MutableBatcherConfig`
- Add `updateBatchSize(int newBatchSize)` method to `MicroBatcher`
- Add `updateLingerTime(Duration newLingerTime)` method to `MicroBatcher`
- Ensure thread-safety for configuration updates
- Notify batch processor of changes

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java` (or create mutable version)
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test dynamic batch size updates
- Test dynamic linger time updates
- Test thread-safety
- Test that changes take effect

**Acceptance Criteria:**
- Configuration can be updated at runtime
- Updates are thread-safe
- Changes take effect correctly
- 100% test coverage

---

#### Task 3.4: Adaptive Batching
**Priority:** Low  
**Estimated Effort:** 8 hours  
**Dependencies:** Task 3.3 (Dynamic Configuration), Task 2.3 (Batch Size Metrics)

**Implementation:**
- Add adaptive batching configuration to `BatcherConfig.Builder`:
  - `adaptiveBatchingEnabled` (boolean)
  - `minBatchSize` (int)
  - `maxBatchSize` (int)
  - `targetLatency` (Duration)
- Implement adaptive algorithm in `MicroBatcher`:
  - Monitor batch dispatch latency
  - Adjust batch size based on latency vs target
  - Respect min/max bounds
  - Use exponential moving average for stability

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java`
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test adaptive algorithm with various scenarios
- Test min/max bounds
- Test target latency tracking

**Acceptance Criteria:**
- Adaptive batching adjusts batch size correctly
- Respects min/max bounds
- Converges toward target latency
- Stable and doesn't oscillate
- 100% test coverage

---

#### Task 3.5: Micrometer Integration Enhancement
**Priority:** Low  
**Estimated Effort:** 4 hours  
**Dependencies:** None

**Implementation:**
- Add support for custom tags in metrics
- Add support for multiple MeterRegistry instances
- Improve metric naming conventions (ensure consistency)
- Add metric filtering capability

**Files to Modify:**
- `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`
- `src/main/java/com/vajrapulse/vortex/BatcherConfig.java` (for custom tags)

**Tests:**
- Update `src/test/groovy/com/vajrapulse/vortex/MicroBatcherSpec.groovy`
- Test custom tags
- Test multiple registries
- Test metric filtering

**Acceptance Criteria:**
- Custom tags work correctly
- Multiple registries supported
- Metric names are consistent
- Filtering works
- 100% test coverage

---

#### Task 3.6: Spring Boot Auto-Configuration
**Priority:** Low  
**Estimated Effort:** 8 hours  
**Dependencies:** None

**Implementation:**
- Create new module `vortex-spring-boot-starter` (or add to main module)
- Create `VortexAutoConfiguration` class
- Create `VortexProperties` for configuration properties
- Add `@ConditionalOnClass` and `@EnableConfigurationProperties` annotations
- Auto-configure `MicroBatcher` beans based on properties
- Add `spring-boot-autoconfigure` dependency

**Files to Create:**
- `src/main/java/com/vajrapulse/vortex/spring/VortexAutoConfiguration.java`
- `src/main/java/com/vajrapulse/vortex/spring/VortexProperties.java`
- `src/main/resources/META-INF/spring.factories` (or use `@AutoConfiguration`)

**Files to Modify:**
- `build.gradle.kts` (add Spring Boot dependencies)

**Tests:**
- Create `src/test/groovy/com/vajrapulse/vortex/spring/VortexAutoConfigurationSpec.groovy`
- Test auto-configuration
- Test property binding
- Test conditional configuration

**Acceptance Criteria:**
- Auto-configuration works
- Properties bind correctly
- Conditional configuration works
- 100% test coverage

---

### Phase 4: Documentation and Examples
**Goal:** Improve user experience with comprehensive documentation

#### Task 4.1: Usage Examples
**Priority:** Medium  
**Estimated Effort:** 6 hours  
**Dependencies:** All Phase 1 and Phase 2 tasks

**Implementation:**
- Create comprehensive usage examples:
  - Basic usage (update existing)
  - Error handling examples
  - Metrics integration examples
  - Testing examples
  - Performance tuning guide
- Update `examples/` directory

**Files to Create/Modify:**
- `examples/ErrorHandlingExample.java`
- `examples/MetricsIntegrationExample.java`
- `examples/TestingExample.java`
- `examples/PerformanceTuningExample.java`
- Update existing examples

**Tests:**
- Ensure all examples compile and run
- Add example tests if appropriate

**Acceptance Criteria:**
- All examples compile and run
- Examples demonstrate best practices
- Examples are well-documented

---

#### Task 4.2: Best Practices Guide
**Priority:** Medium  
**Estimated Effort:** 4 hours  
**Dependencies:** All implementation tasks

**Implementation:**
- Create `documents/guides/BEST_PRACTICES.md`
- Cover:
  - When to use microbatching
  - Optimal batch sizes
  - Linger time tuning
  - Error handling strategies
  - Performance optimization
  - Metrics interpretation

**Files to Create:**
- `documents/guides/BEST_PRACTICES.md`

**Acceptance Criteria:**
- Guide is comprehensive
- Covers all important topics
- Includes examples
- Well-structured and readable

---

## Testing Requirements

### Coverage Requirements
- **Minimum 90% line coverage** for all new code
- **Minimum 80% instruction coverage** overall
- **Minimum 50% branch coverage** for complex methods
- All public APIs must have tests
- Edge cases must be tested

### Test Organization
- One spec file per class being tested
- Use `*Spec.groovy` naming convention
- Follow Given-When-Then structure
- Use `@Unroll` for parameterized tests
- Use `cleanup:` blocks for resource cleanup

## Implementation Timeline

### Week 1: Foundation (Phase 1)
- Days 1-2: Tasks 1.1, 1.2, 1.3 (ItemResult, tracking, error handling)
- Days 3-4: Task 1.4 (Queue metrics)
- Day 5: Testing and bug fixes

### Week 2: Enhanced Features (Phase 2)
- Days 1-2: Task 2.1 (Callbacks)
- Days 3-4: Tasks 2.2, 2.3 (Per-item metrics, batch size metrics)
- Day 5: Task 2.4 (Test utilities)

### Week 3: Advanced Features (Phase 3)
- Days 1-2: Tasks 3.1, 3.2 (Debug mode, retry support)
- Days 3-4: Tasks 3.3, 3.4 (Dynamic config, adaptive batching)
- Day 5: Tasks 3.5, 3.6 (Micrometer enhancements, Spring Boot)

### Week 4: Documentation and Polish (Phase 4)
- Days 1-2: Task 4.1 (Usage examples)
- Day 3: Task 4.2 (Best practices guide)
- Days 4-5: Final testing, bug fixes, release preparation

## Release Checklist

- [ ] All Phase 1 tasks completed and tested
- [ ] All Phase 2 tasks completed and tested
- [ ] All Phase 3 tasks completed and tested
- [ ] All Phase 4 tasks completed
- [ ] All tests passing (>90% coverage)
- [ ] No compiler warnings
- [ ] Documentation updated
- [ ] Examples updated and working
- [ ] README updated
- [ ] Release notes prepared
- [ ] Version bumped to 0.0.2
- [ ] Tagged and released to Maven Central

## Risk Mitigation

### Technical Risks
1. **Performance Impact**: New metrics and features may impact performance
   - **Mitigation**: Make expensive features optional (e.g., per-item metrics)
   - **Mitigation**: Benchmark before and after changes

2. **Breaking Changes**: Some changes may break existing code
   - **Mitigation**: Maintain backward compatibility where possible
   - **Mitigation**: Document any breaking changes clearly

3. **Complexity**: Adaptive batching and dynamic config add complexity
   - **Mitigation**: Thorough testing and documentation
   - **Mitigation**: Start with simple implementations, iterate

### Schedule Risks
1. **Scope Creep**: Additional features may be requested
   - **Mitigation**: Stick to plan, defer non-critical features
   - **Mitigation**: Regular progress reviews

2. **Testing Time**: Comprehensive testing may take longer than estimated
   - **Mitigation**: Write tests alongside implementation
   - **Mitigation**: Allocate buffer time in schedule

## Success Criteria

Release 0.0.2 will be considered successful when:
1. All high and medium priority features are implemented
2. All tests pass with >90% coverage
3. Documentation is comprehensive and up-to-date
4. Examples demonstrate all new features
5. Library is published to Maven Central
6. No critical bugs reported in first week after release

## Notes

- This plan is flexible and may be adjusted based on feedback and priorities
- Some low-priority features may be deferred to 0.0.3 if needed
- Focus on quality over speed - better to delay than ship buggy code
- Regular code reviews and pair programming recommended for complex features

