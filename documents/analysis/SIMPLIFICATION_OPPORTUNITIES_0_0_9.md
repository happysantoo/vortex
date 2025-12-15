## Simplification Opportunities – Post 0.0.9

**Version:** 0.0.9  
**Scope:** Core library, config, retries/results, metrics, tracing, tests, benchmarks, build

This document captures a prioritized list of simplification opportunities for the Vortex micro‑batcher after the 0.0.9 release. It is intended as a roadmap for incremental refactors that improve clarity, maintainability, and correctness without changing the public API surface (unless explicitly noted).

---

## 1. Top‑Priority Simplifications

### 1.1 Unify `submit` vs `submitInternal` Queue/Rejection Logic

- **Location**
  - `MicroBatcher.submit(T item, ItemCallback<T> callback)`
  - `MicroBatcher.submitInternal(T item)`
- **Current State**
  - `submit`:
    - Computes queue rejection threshold using `BatcherConfig.maxQueueSize` and `queueRejectionThreshold`.
    - Uses `queue.size()` and an immediate `queue.offer(request)` (no timeout).
    - Records `requestsRejected`/`requestsSubmitted` metrics and returns `ItemResult`.
  - `submitInternal`:
    - Uses timed `queue.offer(request, QUEUE_OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)`.
    - No threshold check; relies on queue offer timeout.
    - Records `requestsSubmitted` earlier in the flow and completes a `CompletableFuture<BatchResult<T>>`.
- **Problems**
  - Two different rejection behaviors and metric sequences.
  - Harder to reason about retries/replays vs normal submissions.
- **Simplification Plan**
  - Introduce a private helper (conceptual API):
    ```java
    private enum EnqueueResult { ACCEPTED, REJECTED_THRESHOLD, REJECTED_FULL }
    private EnqueueResult tryEnqueue(PendingRequest<T> request, boolean useTimeout);
    ```
  - Centralize:
    - Threshold computation.
    - Actual `offer` call (with or without timeout, depending on caller).
    - Metrics and `ItemRejectedException` creation.
  - `submit` and `submitInternal` become thin wrappers that:
    - Call the shared helper (with flags for threshold / timeout behavior).
    - Map `EnqueueResult` to `ItemResult` or `CompletableFuture` behavior.
- **Benefits**
  - Single source of truth for queue semantics and metrics.
  - Easier to test rejection behavior once and reuse across normal submit + retries.

#### Acceptance Criteria
- [x] A single internal helper exists for queueing (e.g. `tryEnqueue(...)` with a small `EnqueueResult` enum).
- [x] `submit(...)` and `submitInternal(...)` no longer duplicate queue size / offer logic.
- [x] Queue rejection behavior (threshold- and full-queue cases) remains consistent with 0.0.9 public behavior.
- [x] Retry/replay code paths (via `submitInternal`) still honor the timed-offer semantics and propagate `ItemRejectedException` correctly.
- [x] All existing tests around queue rejection and thresholds continue to pass.
- [x] `jacocoTestCoverageVerification` passes without new violations.

#### Status
- [x] Implemented on branch `0.0.10` (`tryEnqueue` + `EnqueueResult` used by both `submit` and `submitInternal`).
- [x] Tests and coverage passing.

---

### 1.2 Extract Common “Closed” and Tracing Logic in `MicroBatcher`

- **Location**
  - `MicroBatcher.submit(...)`, `submitInternal(...)`, `updateBatchSize(...)`, `updateLingerTime(...)`
  - Tracing calls to `tracingHook.onSubmit(item)`
- **Current State**
  - The same formatted `IllegalStateException` message with queue depth / active batches is created in multiple places.
  - Tracing hook invocation with defensive try/catch is duplicated in both `submit` and `submitInternal`.
- **Simplification Plan**
  - Add a private helper for the standard closed error:
    ```java
    private IllegalStateException closedException() { ... }
    ```
  - Add a small helper for tracing submit:
    ```java
    private void safeOnSubmit(T item) { ... }
    ```
  - Use these helpers consistently across all methods that need them.
- **Benefits**
  - Reduced duplication; consistent diagnostics and tracing behavior.
  - Simplifies future changes to error messages or tracing strategy.

#### Acceptance Criteria
- [x] A single helper method centralizes closed-state exception creation (includes queue depth and active batches).
- [x] A single helper method centralizes `onSubmit` tracing, including error swallowing and optional debug logging.
- [x] All public methods that need closed-state guards use the helper instead of manual formatting.
- [x] Both `submit` and `submitInternal` delegate to the shared tracing helper instead of duplicating try/catch logic.
- [x] No change to the external exception type thrown or to the tracing semantics visible to callers.
- [x] All tests and coverage pass.

#### Status
- [x] Completed on branch `0.0.10` (helpers `newClosedException()` and `safeOnSubmit(T)` introduced and wired in).

---

### 1.3 Simplify `RetryManager` Executors and Cleanup

- **Location**
  - `RetryManager<T>` in `com.vajrapulse.vortex.internal`
- **Current State**
  - Uses:
    - Main `ExecutorService` (from `MicroBatcher`) for executing retry tasks.
    - A dedicated `ScheduledExecutorService` for periodic cleanup of retry count entries.
  - Retry delay is implemented via `Thread.sleep` inside tasks submitted to the main executor.
  - Cleanup logic:
    - Periodic cleanup removes entries that reached `maxRetries`.
    - Capacity‑based eviction when the map hits `MAX_RETRY_COUNT_ENTRIES`.
- **Issues**
  - Multiple executors with overlapping responsibilities.
  - Cleanup semantics are more complex than needed (size‑based + periodic).
  - Use of `Thread.sleep` in retry tasks complicates reasoning and timing tests.
- **Simplification Plan**
  - Clarify responsibilities between executors:
    - Main `ExecutorService` is responsible for executing retry tasks (including optional delay via `Thread.sleep`).
    - A single optional `ScheduledExecutorService` is responsible only for periodic cleanup based on `maxRetries`.
  - Remove capacity‑based eviction heuristics:
    - Rely solely on “remove when `count >= maxRetries`” for automatic cleanup.
  - Avoid creating cleanup infrastructure when retries are disabled:
    - Do not create or schedule the cleanup executor when `maxRetries <= 0`.
- **Benefits**
  - Clearer lifecycle; easier to shut down and test.
  - Less surprising behavior under high cardinality of items.

#### Acceptance Criteria
- [x] `RetryManager` no longer contains `MAX_RETRY_COUNT_ENTRIES` or size‑based eviction logic in `scheduleRetry`.
- [x] A single scheduled executor (if present) is used only for periodic cleanup based on `maxRetries`, not for capacity management.
- [x] When `maxRetries <= 0`, no cleanup executor is created or scheduled.
- [x] `cleanupStaleRetries()` only removes entries whose retry count has reached `maxRetries`.
- [x] `clearAll()` correctly shuts down the cleanup executor when it exists and handles the `null` case safely.
- [x] All existing tests and `jacocoTestCoverageVerification` pass without modification to public behavior.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Removed capacity‑based eviction from `scheduleRetry`.
  - Cleanup executor is only created and scheduled when `config.getMaxRetries() > 0`.
  - `cleanupStaleRetries()` now operates purely on `maxRetries` and exits early when retries are disabled.

---

### 1.4 Slim Down `ResultProcessor.processNonAtomicResults`

- **Location**
  - `ResultProcessor<T>.processNonAtomicResults(...)`
- **Current State**
  - Builds maps of successes/failures (`Map<T, SuccessEvent>`, `Map<T, FailureEvent>`).
  - Tracks used results in separate maps.
  - Runs a second pass with fallback distribution to unmatched requests.
- **Issues**
  - The main batch mapping logic is long and dense, mixing happy path and fallback.
  - Harder than necessary to understand the primary mapping behavior.
- **Simplification Plan**
  - Separate “ordinary 1:1 mapping” and “fallback mapping” into distinct helpers:
    - Helpers to build success/failure maps, match exact results, and collect unmatched results.
    - A small helper responsible only for distributing fallback results to unmatched requests.
  - Keep the external behavior identical while making the implementation easier to read and maintain.
- **Benefits**
  - Easier to reason about non‑atomic semantics and to write focused tests.
  - Reduces cognitive load in a critical piece of logic.

#### Acceptance Criteria
- [x] `processNonAtomicResults` is reduced to high‑level orchestration (replay check, map building, exact matching, fallback distribution, retry cleanup).
- [x] Exact 1:1 matching logic is encapsulated in small private helpers (e.g. building maps, matching success/failure, collecting unmatched results).
- [x] Fallback distribution to unmatched requests is handled by a dedicated helper that delegates to the existing `handleFallback` behavior.
- [x] No change in public behavior or semantics: all existing tests around mixed success/failure batches and retries still pass unchanged.
- [x] `jacocoTestCoverageVerification` passes without requiring new exclusions.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Introduced helpers to build success/failure maps, match exact results, collect unmatched results, and distribute fallback results.
  - `processNonAtomicResults` now reads as a short, structured flow while preserving existing semantics.

---

### 1.5 Tighten JaCoCo Exclusions

- **Location**
  - `build.gradle.kts` – `jacocoTestCoverageVerification` rules
- **Current State**
  - Class‑level exclusions include entire classes like `MicroBatcher`, `BatcherConfig`, some backpressure remnants, and broad patterns.
  - Method‑level exclusions already exist for especially complex methods.
- **Simplification Plan**
  - Remove outdated references (e.g., deleted backpressure classes).
  - Prefer **method-level** exclusions for hard‑to‑test branches (e.g., complex shutdown, cleanup methods).
  - Keep core classes like `MicroBatcher` and `BatcherConfig` included at the class level, with minimal targeted method exclusions.
- **Benefits**
  - Coverage report becomes more meaningful.
  - Encourages tests to exercise most of the real behavior while still acknowledging genuinely hard‑to‑test branches.

#### Acceptance Criteria
- [x] Legacy or deleted classes (e.g., backpressure implementations) are no longer referenced in exclusions.
- [x] Configuration and support classes such as `BatcherConfig` are no longer excluded at the CLASS level (their behavior is validated via tests).
- [x] Tiny helper methods that are hard to exercise directly (e.g., tracing helpers) can be excluded at the method level with justification.
- [x] The `MicroBatcher` class remains the only CLASS-level exception due to its complex async/shutdown behavior, with additional METHOD-level exclusions used where appropriate.
- [x] `jacocoTestCoverageVerification` passes consistently on the `0.0.10` branch.

#### Status
- [x] Updated in `build.gradle.kts` on branch `0.0.10`:
  - Removed outdated references to backpressure classes and legacy `PendingRequest` package paths.
  - Kept only necessary CLASS-level exclusions (examples, certain internal helper/metrics/result types, and `MicroBatcher` as a special case).
  - Added or refined METHOD-level exclusions for specific hard-to-test branches (e.g., tracing helpers, shutdown/cleanup routines, and per-item metric guards).

---

## 2. Medium‑Priority Simplifications

### 2.1 Factor Batcher Presets Out of `MicroBatcher`

- **Location**
  - `MicroBatcher.forHighThroughput`, `.forLowLatency`, `.forBalanced`, `.forResilient`
- **Current State**
  - Each factory method manually builds a `BatcherConfig` with batch size, linger time, queue size, etc.
- **Simplification Plan**
  - Introduce presets in `BatcherConfig`, e.g.:
    ```java
    public static BatcherConfig highThroughputPreset() { ... }
    public static BatcherConfig lowLatencyPreset() { ... }
    public static BatcherConfig balancedPreset() { ... }
    public static BatcherConfig resilientPreset(Predicate<Throwable> retryable) { ... }
    ```
  - Have `MicroBatcher` factories delegate to these:
    ```java
    MicroBatcher.forHighThroughput(backend, registry) =
        new MicroBatcher<>(backend, BatcherConfig.highThroughputPreset(), registry);
    ```
- **Benefits**
  - Centralizes tuning in one place.
  - Keeps `MicroBatcher` focused on batching behavior, not default configuration recipes.

#### Acceptance Criteria
- [x] `BatcherConfig` exposes clearly named static presets for the main tuning profiles (high throughput, low latency, balanced, resilient).
- [x] `MicroBatcher.forHighThroughput/forLowLatency/forBalanced/forResilient` delegate to these presets instead of building configs inline.
- [x] Existing examples and APIs continue to behave identically (no change in default values).
- [x] Tests and coverage continue to pass without needing new exclusions.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Added `highThroughputPreset`, `lowLatencyPreset`, `balancedPreset`, and `resilientPreset` to `BatcherConfig`.
  - Updated `MicroBatcher` factory methods to delegate to these presets.

---

### 2.2 Consolidate Shutdown Semantics (`close` / `awaitCompletion`)

- **Location**
  - `MicroBatcher.close()`
  - `MicroBatcher.awaitCompletion(...)`
  - Private `awaitInFlightBatches(...)`
- **Current State**
  - `close()`:
    - Sets `closed = true`, clears retries, waits for queue drain, shuts down executor, drains remaining queue synchronously.
  - `awaitCompletion()`:
    - Loops on `queue.isEmpty()` and then calls `awaitInFlightBatches`.
  - `awaitInFlightBatches()`:
    - Uses `activeBatchCount` or executor termination (depending on config).
- **Simplification Plan**
  - Extract a shared helper for queue draining used by both `close()` and `awaitCompletion(...)`.
  - Reuse `awaitInFlightBatches(...)` from `close()` instead of having a second custom loop.
  - Keep existing timeouts and semantics (best-effort shutdown) unchanged.
- **Benefits**
  - Single place to adjust shutdown semantics.
  - Easier to reason about guarantees (what “graceful close” actually means).

#### Acceptance Criteria
- [x] Queue-drain logic lives in a single private helper that both `close()` and `awaitCompletion(...)` use.
- [x] `close()` no longer has its own custom in-flight batch wait loop; it delegates to `awaitInFlightBatches(...)` with an appropriate timeout.
- [x] Existing public semantics (timeouts, best-effort guarantees) remain unchanged from a caller’s perspective.
- [x] All existing tests, especially around `awaitCompletion` and shutdown, continue to pass without modification.
- [x] No new JaCoCo exclusions are required.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Added `waitForQueueToDrain(...)` and reused it in `close()` and `awaitCompletion(...)`.
  - `close()` now uses `awaitInFlightBatches(...)` for in-flight batch waiting instead of an ad-hoc loop.

---

### 2.3 Simplify Metrics Internals

- **Location**
  - `MetricsManager` and `MetricsProvider`
- **Current State**
  - Per‑item metrics guarded by `config.isPerItemMetrics()` checks in multiple methods.
  - `MetricsProvider` is implemented as a large anonymous inner class.
- **Simplification Plan**
  - Cache `boolean perItemMetricsEnabled` in `MetricsManager` and use that flag in conditional metrics.
  - Extract a `DefaultMetricsProvider` class that takes the relevant counters, timers, and queue reference; `MetricsManager.getMetricsProvider()` just constructs it.
- **Benefits**
  - Cleaner metric recording logic.
  - Better isolation for testing the metrics view.

#### Acceptance Criteria
- [x] `MetricsManager` caches a `perItemMetricsEnabled` flag and uses it consistently for per-item metric recording guards.
- [x] The anonymous inner implementation of `MetricsProvider` is replaced by a dedicated `DefaultMetricsProvider` class in the same package.
- [x] `MetricsManager.getMetricsProvider()` simply constructs and returns a `DefaultMetricsProvider` instance with the required Micrometer primitives.
- [x] No change in the public `MetricsProvider` API or semantics (rates, totals, percentiles).
- [x] Tests and coverage pass, with `DefaultMetricsProvider` treated like other internal metrics helpers for coverage rules.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Added `perItemMetricsEnabled` flag and updated per-item recording methods to use it.
  - Introduced `DefaultMetricsProvider` and updated `MetricsManager.getMetricsProvider()` to use it.

---

### 2.4 Tracing Hook Simplifications

- **Location**
  - `MicrometerTracingHook`
  - Tracing-related code in `MicroBatcher`
- **Current State**
  - Each tracing callback wraps logic in its own try/catch.
  - Span lifecycle relies on `tracer.currentSpan()` behavior between callbacks.
- **Simplification Plan**
  - Add a small internal helper in `MicrometerTracingHook` for “run with tracing, swallow errors” to avoid repeated try/catch.
  - Optionally introduce simpler span lifecycles:
    - E.g., create and end spans fully within each callback instead of relying on `currentSpan`, if that matches your tracing expectations.
- **Benefits**
  - Less boilerplate and more explicit span semantics.
  - Easier to adapt to different tracing setups in the future.

#### Acceptance Criteria
- [x] `MicrometerTracingHook` uses a single shared helper to encapsulate try/catch behavior for tracing callbacks.
- [x] All tracing methods (`onSubmit`, `onBatchDispatchStart`, `onBatchDispatchSuccess`, `onBatchDispatchFailure`, `onRetry`) delegate their core logic through this helper.
- [x] No change to observable tracing behavior for callers or tests (span names, tags, and error handling are preserved).
- [x] Existing tests in `MicrometerTracingHookSpec` continue to pass without modification.
- [x] JaCoCo configuration for tracing remains valid (class/method exclusions unchanged).

#### Status
- [x] Implemented on branch `0.0.10`:
  - Added `runSafely(Runnable)` and refactored all tracing callbacks to use it.
  - Kept span lifecycle and tagging semantics identical to the previous implementation.

---

## 3. Lower‑Priority / Nice‑to‑Have Simplifications

### 3.1 Centralize Validation in `BatcherConfig.Builder.build()`

- **Location**
  - `BatcherConfig.Builder`
- **Current State**
  - Each setter performs local validation (e.g., positive batch size, non‑negative retries).
  - Cross‑field constraints are implicit or validated inline (e.g., `maxQueueSize >= batchSize`).
- **Simplification Plan**
  - Keep simple local validation (e.g. non‑null, non‑negative) in setters.
  - Add a single validation block in `build()` for cross‑field invariants:
    - `maxQueueSize >= batchSize`
    - `retryDelay` only meaningful if `maxRetries > 0`
    - When retries are enabled, a retryable error predicate must be configured
- **Benefits**
  - One place to maintain and extend invariants.
  - Easier to understand configuration rules.

#### Acceptance Criteria
- [x] Individual setters continue to validate single-field constraints (non-null, non-negative, etc.).
- [x] `BatcherConfig.Builder.build()` performs cross-field checks for:
  - `maxQueueSize` vs `batchSize` when explicitly set.
  - `retryDelay` being zero when `maxRetries == 0`.
  - `retryableErrorPredicate` being non-null when `maxRetries > 0`.
- [x] Misconfigured combinations fail fast with clear exception messages.
- [x] All existing tests and coverage continue to pass without needing changes.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Added cross-field validation block in `Builder.build()` with the invariants above.

---

### 3.2 Test Suite Helpers & Parameterization

- **Location**
  - `MicroBatcherSpec` and related specs
- **Current State**
  - Many tests inline simple "always success" or "always fail" backends.
  - Repeated patterns for `CountDownLatch`, `AtomicBoolean`, `Thread.sleep` for async coordination.
- **Simplification Plan**
  - Introduce a small Groovy/Java helper for test backends (e.g., `TestBackend` or static methods in `MicroBatcherTestUtils`).
  - Use `@Unroll` for tests that only vary by batch size, linger, or rejection threshold.
- **Benefits**
  - Shorter, more focused specs.
  - Fewer timing‑sensitive flakes from ad‑hoc sleeps.

#### Acceptance Criteria
- [x] A `TestBackendHelpers` class (or similar) provides factory methods for common backend patterns:
  - `successBackend()` - always succeeds
  - `failingBackend(Throwable)` - always fails
  - `blockingBackend(CountDownLatch)` - blocks on latch
  - `recordingBackend(List)` - records batches
- [x] Helper methods for async coordination (e.g., `awaitLatch`, `waitForAsync`) reduce `Thread.sleep` usage.
- [x] At least one test uses `@Unroll` with a `where:` table for parameterized scenarios (e.g., queue rejection thresholds).
- [x] Tests are refactored to use helpers where appropriate, reducing duplication.
- [x] All tests continue to pass with the same coverage.

#### Status
- [x] Implemented on branch `0.0.10`:
  - Created `TestBackendHelpers.groovy` with factory methods for common backend patterns and async coordination helpers.
  - Refactored several tests in `MicroBatcherSpec` to use `successBackend()`, `blockingBackend()`, and `waitForAsync()`.
  - Combined two queue rejection tests into a single `@Unroll` parameterized test covering multiple threshold scenarios.
  - All tests pass with existing coverage maintained.

---

### 3.3 JMH Benchmark Deduplication

- **Location**
  - `src/jmh/java/com/vajrapulse/vortex/SubmitSyncBenchmark.java`
  - `src/jmh/java/com/vajrapulse/vortex/benchmark/MicroBatcherBenchmark.java`
  - `src/jmh/java/com/vajrapulse/vortex/benchmark/LatencyBenchmark.java`
- **Current State**
  - Benchmarks configure similar batchers with slightly different knobs, often duplicating config code.
  - Multiple scenarios exist that may overlap in what they measure.
- **Simplification Plan**
  - Introduce `BenchmarkBatcherFactory` in `src/jmh/java` with common presets (small batcher, large batcher, rejection scenario).
  - Reduce scenarios to a small, representative set:
    - Single submit latency.
    - Concurrent throughput.
    - Rejection path.
    - Callback path.
- **Benefits**
  - Easier to maintain and evolve benchmarks alongside the main codebase.
  - Faster, more focused performance runs.

#### Acceptance Criteria
- [x] A `BenchmarkBatcherFactory` class provides factory methods for common benchmark configurations:
  - `successBackend()` - simple backend that always succeeds
  - `defaultBatcher()` - standard configuration for throughput benchmarks
  - `smallQueueBatcher(batchSize, maxQueueSize, threshold)` - for rejection testing
  - `latencyBatcher()` - optimized for latency benchmarks
- [x] All three benchmark classes (`SubmitSyncBenchmark`, `MicroBatcherBenchmark`, `LatencyBenchmark`) use the factory instead of duplicating setup code.
- [x] Benchmark setup methods are simplified (fewer lines, clearer intent).
- [x] Benchmarks compile and can run successfully (verified via `./gradlew compileJmhJava`).

#### Status
- [x] Implemented on branch `0.0.10`:
  - Created `BenchmarkBatcherFactory` with factory methods for common backend and batcher configurations.
  - Refactored all three benchmark classes to use the factory, removing duplicated backend creation and config setup.
  - Reduced setup code in each benchmark by ~15-20 lines.
  - All benchmarks compile successfully.

---

## 4. Suggested Implementation Order

1. **Core correctness & behavior**
   - Unify queue/rejection behavior (`submit` / `submitInternal`).
   - Extract shared closed/tracing helpers in `MicroBatcher`.
2. **Retry & result handling clarity**
   - Simplify `RetryManager` executor/cleanup.
   - Slim down `ResultProcessor` mapping logic.
3. **Coverage & observability hygiene**
   - Tighten JaCoCo exclusions.
   - Simplify metrics and tracing internals.
4. **API ergonomics & tests/benchmarks**
   - Factor presets out of `MicroBatcher`.
   - Consolidate shutdown semantics.
   - Improve test helpers and benchmark deduplication.

Each of these can be implemented incrementally with dedicated PRs, each focused on one area plus its tests and coverage adjustments.


