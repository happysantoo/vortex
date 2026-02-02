# Release 0.0.16: Critical Hardening (Circuit/Shutdown/Timing/Publishing)

**Release Date**: January 29, 2026  
**Version**: 0.0.16

## Overview

0.0.16 is a hardening release that addresses concurrency and shutdown edge cases and improves release workflow reliability.

## Fixed

### Circuit Breaker
- **Single-probe HALF_OPEN**: HALF_OPEN now allows exactly one probe request at a time (prevents multiple concurrent “probe” calls).
- **Reduced TOCTOU behavior**: Circuit breaker gating is evaluated at dispatch execution time (not pre-submit), making behavior more predictable under concurrency.

### Shutdown Safety
- **Bounded final dispatch**: The final synchronous dispatch during shutdown is now bounded by `shutdownFinalDispatchTimeout` (default 2s) to prevent shutdown hangs when the backend blocks.

### Batch Formation
- **Monotonic linger deadline**: Batch formation now tracks linger deadline using `System.nanoTime()` to avoid issues from wall-clock adjustments.

### Publishing Workflow
- **Central publish script robustness**: `scripts/publish-to-central.sh` no longer fails if the GitHub release already exists (Maven Central upload remains the source of truth).

## Config Additions

- `BatcherConfig.shutdownFinalDispatchTimeout(Duration)` (default: 2s)

## Full Changelog

See [CHANGELOG.md](../../CHANGELOG.md).

