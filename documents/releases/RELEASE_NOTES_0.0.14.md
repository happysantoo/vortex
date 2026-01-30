# Release 0.0.14: Backpressure Reliability, Resilience, and Retry Enhancements

**Release Date**: January 29, 2026  
**Version**: 0.0.14

## Overview

Version 0.0.14 focuses on improving reliability under load (backpressure correctness + observability) and resilience (optional circuit breaker + enhanced retry backoff), while keeping the API surface small and configuration-driven.

## Key Features

### Backpressure Reliability Improvements

- **TOCTOU fix in enqueue path**: enqueue now relies on the atomic `queue.offer()` result and classifies rejection afterwards to avoid inconsistent accept/reject behavior under contention.
- **Accurate rejection diagnostics**: `EnqueueResult` now captures `queueSizeAtRejection` so error messages and metrics reflect the actual state at rejection time.
- **Optional early concurrent-batch rejection**: enable `earlyConcurrentBatchRejection(true)` to reject earlier when concurrent dispatch capacity is saturated.

### Observability Improvements

- **Backpressure counters**:
  - `vortex.backpressure.threshold.hits`
  - `vortex.backpressure.full.hits`
  - `vortex.backpressure.concurrent.hits`
- **Gauges**:
  - `vortex.queue.utilization`
  - `vortex.backpressure.rejection.rate`
  - `vortex.processor.healthy`

### Circuit Breaker (Optional)

An opt-in three-state circuit breaker (CLOSED/OPEN/HALF_OPEN) that fails fast on repeated backend failures:

- Config:
  - `circuitBreakerEnabled(true)`
  - `circuitBreakerFailureThreshold(int)`
  - `circuitBreakerOpenDuration(Duration)`
- Metrics:
  - `vortex.circuit.state` (0=closed, 1=open, 2=half_open)
  - `vortex.circuit.open.events`
- Rejections use `ItemRejectedException.circuitOpen()`.

### Enhanced Retry Backoff

Adds configurable retry delay strategy:

- `BatcherConfig.RetryBackoffStrategy`:
  - `FIXED` (default): always `retryDelay`
  - `EXPONENTIAL`: `retryDelay * 2^(attempt-1)` capped by `retryMaxDelay`
- New builder options:
  - `retryBackoffStrategy(...)`
  - `retryMaxDelay(...)`

## Behavior Change Note (submitAsync)

`submitAsync()` now **returns a `CompletableFuture<ItemResult<T>>` that completes with `ItemResult.Failure`** when the underlying batch future completes exceptionally (e.g., circuit open, dispatch rejection, closed/null), instead of completing the returned future exceptionally.

If you previously relied on `future.exceptionally(...)` for these cases, switch to checking `result instanceof ItemResult.Failure` in your `thenApply/thenAccept` chains.

## Quality Metrics

- ✅ All tests passing
- ✅ Coverage verification passing (`jacocoTestCoverageVerification`)
- ✅ JMH benchmarks run successfully (`./gradlew jmh`)

## Full Changelog

See [CHANGELOG.md](../../CHANGELOG.md) for full details.

