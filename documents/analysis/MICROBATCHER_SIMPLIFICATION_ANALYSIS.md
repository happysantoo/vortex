# MicroBatcher Simplification Analysis

## Document Purpose

This document provides a comprehensive analysis of `MicroBatcher.java` to identify simplification opportunities, document current state, identify gaps, and provide a detailed task list for refactoring.

**Analysis Date**: Current (Post 0.0.10 simplification items 3.2, 3.3)  
**Target**: Simplify `MicroBatcher` by removing dynamic configuration updates and other unnecessary complexity

---

## 1. Current State Analysis

### 1.1 Class Structure Overview

**File**: `src/main/java/com/vajrapulse/vortex/MicroBatcher.java`  
**Lines**: 1,204  
**Public Methods**: 15  
**Private Methods**: 12  
**Fields**: 17

### 1.2 Core Components

#### Fields (17 total)

**Configuration & Dependencies:**
- `backend` (Backend<T>) - Backend implementation
- `config` (BatcherConfig) - Immutable configuration
- `meterRegistry` (MeterRegistry) - Metrics registry
- `queue` (BlockingQueue<PendingRequest<T>>) - Internal queue
- `executor` (ExecutorService) - Virtual thread executor

**Dynamic Configuration (Mutable):**
- `currentBatchSize` (volatile int) - **CANDIDATE FOR REMOVAL**
- `currentLingerTime` (volatile Duration) - **CANDIDATE FOR REMOVAL**

**Concurrent Dispatch Limiting:**
- `dispatchSemaphore` (Semaphore) - Optional semaphore for limiting (created from config)
- `activeBatchCount` (AtomicInteger) - Current active batch count
- ~~`maxConcurrentBatches` (int)~~ - **CANDIDATE FOR REMOVAL** (can use `config.getMaxConcurrentBatches()`)

**State & Observability:**
- `closed` (volatile boolean) - Shutdown state
- `debugMode` (boolean) - Cached debug flag
- `tracingHook` (BatchTracingHook) - Optional tracing hook

**Helper Classes:**
- `metrics` (MetricsManager) - Metrics management
- `retryManager` (RetryManager<T>) - Retry logic
- `resultProcessor` (ResultProcessor<T>) - Result processing

#### Public Methods (15 total)

**Constructors:**
1. `MicroBatcher(Backend<T>, BatcherConfig)` - Default constructor
2. `MicroBatcher(Backend<T>, BatcherConfig, MeterRegistry)` - Full constructor

**Factory Methods (4) - CANDIDATE FOR REMOVAL:**
3. ~~`forHighThroughput(Backend<T>, MeterRegistry)`~~ - **REMOVE**
4. ~~`forLowLatency(Backend<T>, MeterRegistry)`~~ - **REMOVE**
5. ~~`forBalanced(Backend<T>, MeterRegistry)`~~ - **REMOVE**
6. ~~`forResilient(Backend<T>, MeterRegistry, Predicate<Throwable>)`~~ - **REMOVE**

**Submission Methods (2):**
7. `submit(T item)` - Fire-and-forget submission
8. `submit(T item, ItemCallback<T> callback)` - Submission with callback

**Dynamic Configuration Methods (4) - CANDIDATE FOR REMOVAL:**
9. `updateBatchSize(int)` - **REMOVE**
10. `updateLingerTime(Duration)` - **REMOVE**
11. `getCurrentBatchSize()` - **REMOVE**
12. `getCurrentLingerTime()` - **REMOVE**

**Observability Methods (3):**
13. `getQueueDepth()` - Get current queue depth
14. `getMetricsProvider()` - Get metrics provider
15. `diagnostics()` - Get diagnostics view

**Lifecycle Methods (2):**
16. `close()` - Shutdown batcher
17. `awaitCompletion(long, TimeUnit)` - Wait for completion

**Utility Methods:**
18. `isClosed()` - Check if closed
19. `getMeterRegistry()` - Get meter registry
20. `getConfig()` - Get configuration

#### Private Methods (12 total)

**Helper Methods:**
1. `newClosedException()` - Create closed exception with context
2. `safeOnSubmit(T)` - Safe tracing hook invocation
3. `tryEnqueue(PendingRequest, boolean, boolean)` - Unified enqueue logic
4. `submitInternal(T)` - Internal submission for retries/replays

**Batch Processing:**
5. `startBatchProcessor()` - Start batch processing loop
6. `processBatch()` - Process a single batch
7. `dispatchBatch(List<PendingRequest>)` - Dispatch batch to backend
8. `handleDispatchRejection(List<PendingRequest>)` - Handle dispatch rejection

**Shutdown & Waiting:**
9. `awaitInFlightBatches(long, TimeUnit)` - Wait for in-flight batches
10. `waitForQueueToDrain(long, TimeUnit)` - Wait for queue to drain

**Enums:**
11. `EnqueueResult` - Enum for enqueue outcomes

### 1.3 Current Functionality

#### Core Batching Logic
- ✅ Queue-based batching with configurable size and time
- ✅ Virtual thread executor for async processing
- ✅ Immediate rejection for backpressure
- ✅ Callback support for individual item results
- ✅ Retry and replay support
- ✅ Metrics and observability

#### Dynamic Configuration (User Doesn't Want)
- ❌ Runtime batch size updates (`updateBatchSize`)
- ❌ Runtime linger time updates (`updateLingerTime`)
- ❌ Separate `currentBatchSize`/`currentLingerTime` fields
- ❌ Getters for current values that may differ from config

#### Factory Methods (User Doesn't Want)
- ❌ `forHighThroughput()` - Users can use constructors with presets
- ❌ `forLowLatency()` - Users can use constructors with presets
- ❌ `forBalanced()` - Users can use constructors with presets
- ❌ `forResilient()` - Users can use constructors with presets

#### Field Redundancy
- ❌ `maxConcurrentBatches` field - Can use `config.getMaxConcurrentBatches()` directly

#### Advanced Features
- ✅ Concurrent batch limiting (optional)
- ✅ Atomic commit mode
- ✅ Auto-replay successes
- ✅ Per-item metrics (optional)
- ✅ Debug mode
- ✅ Tracing hooks
- ✅ Diagnostics API

---

## 2. What's Missing

### 2.1 API Gaps

**No explicit batch submission API:**
- Currently only supports individual item submission
- No way to submit multiple items as a logical batch unit
- (Note: This was attempted but reverted - user may want simpler approach)

**No CompletableFuture-based async API:**
- Only callback-based async handling
- No direct CompletableFuture support for chaining/composition
- (Note: This was attempted but reverted)

### 2.2 Documentation Gaps

**JavaDoc inconsistencies:**
- Some methods reference dynamic updates that may be removed
- `MetricsProvider` JavaDoc references `updateBatchSize()` which may be removed
- `BatcherDiagnostics` JavaDoc references dynamic updates

**Example code:**
- Examples in README reference dynamic updates
- May need updating if dynamic updates are removed

### 2.3 Test Coverage Gaps

**Dynamic update tests:**
- Tests exist for `updateBatchSize` and `updateLingerTime`
- These will need to be removed or updated

---

## 3. Simplification Opportunities

### 3.1 Remove Dynamic Configuration (HIGH PRIORITY)

**Current State:**
- `currentBatchSize` and `currentLingerTime` are volatile fields
- Initialized from `config.getBatchSize()` and `config.getLingerTime()`
- Can be updated at runtime via `updateBatchSize()` and `updateLingerTime()`
- Used in `processBatch()` instead of reading from config

**Simplification:**
- Remove `currentBatchSize` and `currentLingerTime` fields
- Remove `updateBatchSize()` and `updateLingerTime()` methods
- Remove `getCurrentBatchSize()` and `getCurrentLingerTime()` methods
- Update `processBatch()` to use `config.getBatchSize()` and `config.getLingerTime()` directly
- Update `diagnostics()` to use config values
- Update `BatcherDiagnostics` interface to remove dynamic update references

**Impact:**
- **Reduces complexity**: Eliminates mutable state
- **Improves thread safety**: One less source of mutable state
- **Simplifies code**: No need to track "current" vs "config" values
- **Reduces memory**: Two fewer fields
- **Breaking change**: Removes public API methods (acceptable pre-1.0)

**Files Affected:**
- `MicroBatcher.java` - Remove methods and fields
- `BatcherDiagnostics.java` - Update interface (or keep but use config values)
- `MetricsProvider.java` - Update JavaDoc
- `README.md` - Remove examples
- `MicroBatcherSpec.groovy` - Remove/update tests
- Any examples using dynamic updates

### 3.2 Simplify Field Access Patterns

**Current State:**
- Code sometimes uses `currentBatchSize`, sometimes `config.getBatchSize()`
- Inconsistent access patterns

**Simplification:**
- Always use `config.getBatchSize()` and `config.getLingerTime()`
- Remove all references to dynamic fields

**Impact:**
- **Consistency**: Single source of truth
- **Clarity**: Clear that config is immutable

### 3.3 Simplify Diagnostics Implementation

**Current State:**
- `diagnostics()` creates anonymous inner class
- Returns `getCurrentBatchSize()` and `getCurrentLingerTime()`

**Simplification Options:**

**Option A: Keep interface, use config values**
- Update `BatcherDiagnostics` to return config values
- Rename methods to `getBatchSize()` and `getLingerTime()` (remove "Current")
- Update JavaDoc to clarify these are from config

**Option B: Simplify to record**
- Convert `BatcherDiagnostics` to a record
- Return config values directly

**Option C: Remove diagnostics() if not used**
- Check if `diagnostics()` is actually used
- If only used in tests/examples, consider removing

**Impact:**
- **Clarity**: Makes it clear values come from config
- **Simplicity**: Removes confusion about "current" vs "config"

### 3.4 Consolidate Shutdown Logic

**Current State:**
- `close()` has multiple waiting mechanisms:
  - `waitForQueueToDrain()` - Polls queue
  - `executor.shutdown()` and `awaitTermination()` - Waits for executor
  - `awaitInFlightBatches()` - Waits for active batches
  - Synchronous processing of remaining items

**Simplification Opportunities:**
- Could consolidate waiting logic
- `waitForQueueToDrain()` uses `Thread.sleep()` - could use more efficient waiting
- Multiple timeout handling could be unified

**Impact:**
- **Maintainability**: Easier to understand shutdown flow
- **Performance**: More efficient waiting mechanisms

### 3.5 Remove Factory Methods

**Current State:**
- Four factory methods: `forHighThroughput`, `forLowLatency`, `forBalanced`, `forResilient`
- All delegate to `BatcherConfig` preset methods
- Only used in tests, not in production code

**Simplification:**
- Remove all 4 factory methods
- Users can create batchers using constructors with `BatcherConfig` presets
- Example: `new MicroBatcher<>(backend, BatcherConfig.highThroughputPreset(), registry)`

**Impact:**
- **Reduces API surface**: 4 fewer public methods
- **More explicit**: Users see they're using config presets
- **Simpler code**: Less code to maintain
- **Breaking change**: Removes public API methods (acceptable pre-1.0)

**Files Affected:**
- `MicroBatcher.java` - Remove 4 factory methods (~120 lines)
- `MicroBatcherFactoryMethodsSpec.groovy` - Remove/update tests
- `BatcherConfig.java` - Update JavaDoc references
- Examples (if any use factory methods)

### 3.6 Simplify Concurrent Batch Limiting Fields

**Current State:**
- Three fields for concurrent batch limiting:
  - `maxConcurrentBatches` (int) - The limit value from config
  - `dispatchSemaphore` (Semaphore) - Enforcement mechanism
  - `activeBatchCount` (AtomicInteger) - Counter for metrics
- `maxConcurrentBatches` is stored as a field but only used in:
  - Creating the semaphore (line 117)
  - Error messages (lines 710, 840)

**Simplification:**
- Remove `maxConcurrentBatches` field
- Use `config.getMaxConcurrentBatches()` directly when needed
- Keep `dispatchSemaphore` and `activeBatchCount` (they're the actual implementation)

**Impact:**
- **Reduces fields**: One less field to maintain
- **Single source of truth**: Always read from config
- **Clarity**: Makes it clear the limit comes from config
- **No functional change**: Semaphore and counter still work the same

**Files Affected:**
- `MicroBatcher.java` - Remove field, update usages

### 3.7 Remove Unused Imports

**Current State:**
- `LockSupport` is imported but not used (line 26)
- Check for other unused imports

**Simplification:**
- Remove unused imports

**Impact:**
- **Cleanliness**: Cleaner imports

### 3.8 Simplify Exception Handling in close()

**Current State:**
- `close()` has nested try-catch blocks
- Multiple interruption handling points

**Simplification:**
- Could consolidate exception handling
- Ensure consistent interruption handling

**Impact:**
- **Clarity**: Clearer exception handling flow

### 3.9 Consider Removing awaitCompletion() if Unused

**Current State:**
- `awaitCompletion()` provides way to wait before closing
- May not be commonly used

**Simplification:**
- Check usage in codebase
- If only used in tests, consider removing or making it simpler

**Impact:**
- **API Surface**: Reduces public API if unused

---

## 4. Detailed Task List

### Task 1: Remove Dynamic Batch Size Updates

**Priority**: HIGH  
**Effort**: Medium  
**Breaking Change**: Yes

**Steps:**
1. Remove `currentBatchSize` field (line 58)
2. Remove `updateBatchSize(int)` method (lines 1077-1093)
3. Remove `getCurrentBatchSize()` method (lines 1129-1131)
4. Update `processBatch()` to use `config.getBatchSize()` instead of `currentBatchSize` (line 644)
5. Update `diagnostics()` to use `config.getBatchSize()` (line 1176)
6. Update `BatcherDiagnostics` interface:
   - Option A: Rename `getCurrentBatchSize()` to `getBatchSize()` and update JavaDoc
   - Option B: Remove method if not needed
7. Update constructor to remove initialization of `currentBatchSize` (line 131)
8. Update `newClosedException()` if it references currentBatchSize (check line 362)
9. Update tests in `MicroBatcherSpec.groovy`:
   - Remove tests for `updateBatchSize()`
   - Update tests that use `getCurrentBatchSize()`
10. Update `README.md` to remove dynamic batch size examples
11. Update `MetricsProvider.java` JavaDoc to remove `updateBatchSize()` references (line 25)
12. Check examples for usage and update/remove

**Acceptance Criteria:**
- [ ] No `currentBatchSize` field exists
- [ ] No `updateBatchSize()` method exists
- [ ] No `getCurrentBatchSize()` method exists
- [ ] All code uses `config.getBatchSize()` directly
- [ ] All tests pass
- [ ] Coverage requirements met
- [ ] JavaDoc updated
- [ ] README updated
- [ ] CHANGELOG updated (breaking change)

---

### Task 2: Remove Dynamic Linger Time Updates

**Priority**: HIGH  
**Effort**: Medium  
**Breaking Change**: Yes

**Steps:**
1. Remove `currentLingerTime` field (line 59)
2. Remove `updateLingerTime(Duration)` method (lines 1106-1122)
3. Remove `getCurrentLingerTime()` method (lines 1138-1140)
4. Update `processBatch()` to use `config.getLingerTime()` instead of `currentLingerTime` (lines 647-648)
5. Update `diagnostics()` to use `config.getLingerTime()` (line 1181)
6. Update `BatcherDiagnostics` interface:
   - Option A: Rename `getCurrentLingerTime()` to `getLingerTime()` and update JavaDoc
   - Option B: Remove method if not needed
7. Update constructor to remove initialization of `currentLingerTime` (line 132)
8. Update tests in `MicroBatcherSpec.groovy`:
   - Remove tests for `updateLingerTime()`
   - Update tests that use `getCurrentLingerTime()`
9. Update `README.md` to remove dynamic linger time examples
10. Check examples for usage and update/remove

**Acceptance Criteria:**
- [ ] No `currentLingerTime` field exists
- [ ] No `updateLingerTime()` method exists
- [ ] No `getCurrentLingerTime()` method exists
- [ ] All code uses `config.getLingerTime()` directly
- [ ] All tests pass
- [ ] Coverage requirements met
- [ ] JavaDoc updated
- [ ] README updated
- [ ] CHANGELOG updated (breaking change)

---

### Task 3: Update BatcherDiagnostics Interface

**Priority**: MEDIUM  
**Effort**: Low  
**Breaking Change**: Yes (if method names change)

**Steps:**
1. Check usage of `BatcherDiagnostics` in codebase (health checks, tests)
2. Decide on approach:
   - **Option A**: Keep methods, rename to `getBatchSize()` and `getLingerTime()`, update JavaDoc
   - **Option B**: Remove methods entirely if not needed
3. If Option A:
   - Rename `getCurrentBatchSize()` to `getBatchSize()` in interface
   - Rename `getCurrentLingerTime()` to `getLingerTime()` in interface
   - Update JavaDoc to clarify these return config values (not dynamic)
   - Update `MicroBatcher.diagnostics()` implementation
3. If Option B:
   - Remove methods from interface
   - Update `MicroBatcher.diagnostics()` implementation
   - Check usage in codebase and update
4. Update `BatcherHealth.java` if it uses these methods
5. Update tests

**Acceptance Criteria:**
- [ ] Interface updated (renamed or removed methods)
- [ ] JavaDoc clarifies values come from config (not dynamic)
- [ ] All internal usages updated (health checks, tests)
- [ ] All tests pass

---

### Task 4: Clean Up Unused Imports

**Priority**: LOW  
**Effort**: Very Low  
**Breaking Change**: No

**Steps:**
1. Check for `LockSupport` usage (imported on line 26)
2. Remove if unused
3. Check for other unused imports
4. Run linter/IDE to identify unused imports

**Acceptance Criteria:**
- [ ] No unused imports
- [ ] Code compiles

---

### Task 5: Update Documentation

**Priority**: MEDIUM  
**Effort**: Low  
**Breaking Change**: No (documentation only)

**Steps:**
1. Update `README.md`:
   - Remove section on dynamic batch size/linger time updates
   - Update examples that reference these methods
   - Update `MetricsProvider` examples that reference `updateBatchSize()`
2. Update `CHANGELOG.md` if needed
3. Update JavaDoc comments:
   - Remove references to dynamic updates
   - Clarify that config is immutable after construction
4. Update example files if they use dynamic updates

**Acceptance Criteria:**
- [ ] README updated
- [ ] JavaDoc updated
- [ ] Examples updated
- [ ] No references to removed methods

---

### Task 6: Update Tests

**Priority**: HIGH  
**Effort**: Medium  
**Breaking Change**: No (test changes only)

**Steps:**
1. Remove tests for `updateBatchSize()` and `updateLingerTime()`
2. Update tests that use `getCurrentBatchSize()` or `getCurrentLingerTime()`
3. Update tests that verify dynamic updates
4. Add tests to verify config values are used correctly
5. Ensure coverage requirements are met

**Acceptance Criteria:**
- [ ] All tests pass
- [ ] Coverage requirements met
- [ ] No tests for removed methods
- [ ] Tests verify config values are used

---

### Task 7: Simplify Shutdown Logic (Optional)

**Priority**: LOW  
**Effort**: Medium  
**Breaking Change**: No (internal only)

**Steps:**
1. Review `close()` method complexity
2. Consider consolidating waiting logic
3. Improve `waitForQueueToDrain()` efficiency (currently uses `Thread.sleep()`)
4. Ensure consistent interruption handling

**Acceptance Criteria:**
- [ ] Shutdown logic is clear and maintainable
- [ ] All tests pass
- [ ] No performance regression

---

### Task 8: Remove Factory Methods

**Priority**: MEDIUM  
**Effort**: Low  
**Breaking Change**: Yes

**Steps:**
1. Remove `forHighThroughput()` method (lines 197-199)
2. Remove `forLowLatency()` method (lines 236-238)
3. Remove `forBalanced()` method (lines 275-277)
4. Remove `forResilient()` method (lines 320-326)
5. Remove/update `MicroBatcherFactoryMethodsSpec.groovy`:
   - Option A: Remove entire test file
   - Option B: Update tests to use constructors with presets
6. Update `BatcherConfig.java` JavaDoc to remove factory method references
7. Check examples for usage and update
8. Update README if it references factory methods

**Acceptance Criteria:**
- [ ] All 4 factory methods removed
- [ ] Test file removed or updated
- [ ] JavaDoc updated
- [ ] Examples updated
- [ ] README updated
- [ ] All tests pass

---

### Task 9: Simplify Concurrent Batch Limiting Fields

**Priority**: MEDIUM  
**Effort**: Low  
**Breaking Change**: No (internal only)

**Steps:**
1. Remove `maxConcurrentBatches` field (line 52)
2. Remove initialization of `maxConcurrentBatches` (line 115)
3. Update `dispatchBatch()` to use `config.getMaxConcurrentBatches()`:
   - Line 710: Debug log message
4. Update `handleDispatchRejection()` to use `config.getMaxConcurrentBatches()`:
   - Line 840: Error message
5. Update semaphore creation to use `config.getMaxConcurrentBatches()` directly (line 117)
6. Verify all usages updated

**Acceptance Criteria:**
- [ ] No `maxConcurrentBatches` field exists
- [ ] All code uses `config.getMaxConcurrentBatches()` directly
- [ ] Semaphore and counter still work correctly
- [ ] All tests pass

---

## 5. Implementation Order

### Phase 1: Core Removal (Must Do)
1. **Task 1**: Remove Dynamic Batch Size Updates
2. **Task 2**: Remove Dynamic Linger Time Updates
3. **Task 6**: Update Tests

### Phase 2: API Simplification (Should Do)
4. **Task 8**: Remove Factory Methods
5. **Task 9**: Simplify Concurrent Batch Limiting Fields
6. **Task 3**: Update BatcherDiagnostics Interface
7. **Task 5**: Update Documentation

### Phase 3: Cleanup (Nice to Have)
8. **Task 4**: Clean Up Unused Imports
9. **Task 7**: Simplify Shutdown Logic (Optional)
10. **Task 9**: Consider Removing awaitCompletion() if Unused (Optional)

---

## 6. Risk Assessment

**Note**: Pre-1.0 release - no external clients. Breaking changes are acceptable.

### Low Risk
- **Removing public API methods**: Breaking change, but acceptable pre-1.0
  - **Mitigation**: Document in CHANGELOG
  - **Impact**: None (no external users)

### Low Risk
- **BatcherDiagnostics interface changes**: May affect internal health checks
  - **Mitigation**: Update internal usage, comprehensive testing
  - **Impact**: Minimal - only affects internal code

### Low Risk
- **Internal refactoring**: Should not affect functionality
  - **Mitigation**: Comprehensive testing
  - **Impact**: None if tests pass

---

## 7. Benefits Summary

### Code Simplification
- **Reduced complexity**: Remove mutable state
- **Improved clarity**: Single source of truth (config)
- **Better thread safety**: Less mutable state to synchronize
- **Smaller API surface**: Fewer methods to maintain

### Performance
- **Reduced memory**: Two fewer fields
- **No volatile reads**: Direct config access (though config is immutable anyway)

### Maintainability
- **Easier to understand**: No confusion about "current" vs "config"
- **Easier to test**: Less state to verify
- **Easier to document**: Simpler API

---

## 8. Migration Guide (For Internal Code)

**Note**: Pre-1.0 release - no external clients. This is for internal code only.

If internal code is using dynamic updates, update as follows:

1. **Remove calls to `updateBatchSize()` and `updateLingerTime()`**
2. **Create new MicroBatcher instance with desired config** if different settings are needed
3. **Update code that uses `getCurrentBatchSize()` or `getCurrentLingerTime()`** to use `getConfig().getBatchSize()` or `getConfig().getLingerTime()`
4. **Update health checks** if they use `BatcherDiagnostics.getCurrentBatchSize()` or `getCurrentLingerTime()`

---

## 9. Open Questions

1. **Should `BatcherDiagnostics` methods be renamed or removed?**
   - Recommendation: Rename to `getBatchSize()` and `getLingerTime()` for clarity

2. **Should we deprecate methods before removing?**
   - Recommendation: **No** - Pre-1.0, can remove directly. No deprecation needed.

3. **Are factory methods still needed?**
   - Recommendation: Yes, they're convenient and already simplified

4. **Should `awaitCompletion()` be kept?**
   - Recommendation: Check usage first, keep if used

---

## 10. Success Criteria

The simplification is successful when:

- [ ] Dynamic batch size/linger time updates are completely removed
- [ ] All code uses `config.getBatchSize()` and `config.getLingerTime()` directly
- [ ] No `currentBatchSize` or `currentLingerTime` fields exist
- [ ] All tests pass with >90% coverage
- [ ] Documentation is updated and accurate
- [ ] Internal code updated (tests, examples, health checks)
- [ ] Code is simpler and easier to understand
- [ ] Performance is maintained or improved
- [ ] CHANGELOG updated with breaking changes

---

## Appendix: Code References

### Key Locations

**Dynamic Configuration Fields:**
- Line 58: `private volatile int currentBatchSize;`
- Line 59: `private volatile Duration currentLingerTime;`
- Line 131: `this.currentBatchSize = config.getBatchSize();`
- Line 132: `this.currentLingerTime = config.getLingerTime();`

**Concurrent Batch Limiting Field:**
- Line 52: `private final int maxConcurrentBatches;`
- Line 115: `this.maxConcurrentBatches = config.getMaxConcurrentBatches();`

**Dynamic Configuration Methods:**
- Lines 1077-1093: `updateBatchSize(int)`
- Lines 1106-1122: `updateLingerTime(Duration)`
- Lines 1129-1131: `getCurrentBatchSize()`
- Lines 1138-1140: `getCurrentLingerTime()`

**Factory Methods:**
- Lines 197-199: `forHighThroughput(Backend<T>, MeterRegistry)`
- Lines 236-238: `forLowLatency(Backend<T>, MeterRegistry)`
- Lines 275-277: `forBalanced(Backend<T>, MeterRegistry)`
- Lines 320-326: `forResilient(Backend<T>, MeterRegistry, Predicate<Throwable>)`

**Concurrent Batch Limiting Usage:**
- Line 115: `this.maxConcurrentBatches = config.getMaxConcurrentBatches();`
- Line 117: `new Semaphore(maxConcurrentBatches)`
- Line 710: Debug log with `maxConcurrentBatches`
- Line 840: Error message with `maxConcurrentBatches`

**Usage of Dynamic Fields:**
- Line 644: `int batchSize = currentBatchSize;` in `processBatch()`
- Line 647: `Duration lingerTime = currentLingerTime;` in `processBatch()`
- Line 1176: `return currentBatchSize;` in `diagnostics()`
- Line 1181: `return currentLingerTime;` in `diagnostics()`

**Related Interfaces:**
- `BatcherDiagnostics.java`: Interface with `getCurrentBatchSize()` and `getCurrentLingerTime()`
- `MetricsProvider.java`: JavaDoc references `updateBatchSize()`

**Related Test Files:**
- `MicroBatcherFactoryMethodsSpec.groovy`: Tests for factory methods (to be removed/updated)

