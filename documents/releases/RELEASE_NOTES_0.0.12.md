# Release 0.0.12: Code Simplification, Performance Optimizations, and Quality Improvements

**Release Date**: December 26, 2025  
**Version**: 0.0.12

## Overview

Version 0.0.12 focuses on code simplification, performance optimizations, and improved observability through named virtual threads. This release maintains 100% backward compatibility while significantly improving code quality and performance.

## Key Features

### Named Virtual Threads

All virtual threads now have descriptive names with "vortex-" prefix for improved observability:

- Thread naming pattern: `vortex-{instanceId}-{type}-{N}`
- Batch processor thread: `vortex-{id}-batch-processor`
- Dispatch worker threads: `vortex-{id}-dispatch-0`, `vortex-{id}-dispatch-1`, etc.
- Retry worker threads: `vortex-{id}-retry-0`, `vortex-{id}-retry-1`, etc.
- Each MicroBatcher instance gets a unique ID for thread naming

**Benefits**:
- Improves debugging with clear thread identification in thread dumps
- Better APM/monitoring tool integration
- Easier troubleshooting in production environments

### Separate Executors

Internal architecture now uses separate executors for dispatch and retry operations:
- `dispatchExecutor`: Handles backend dispatch operations with named threads
- `retryExecutor`: Handles retry operations with named threads
- Batch processor runs on a dedicated named virtual thread
- No performance impact (virtual threads share the same carrier thread pool)

## Code Quality Improvements

### Code Simplification

- **Java Records**: Converted `SuccessEvent`, `FailureEvent`, `SubmissionContext`, and `PendingRequest` to Java records
- **Removed Boilerplate**: Eliminated redundant getter methods (records provide accessors automatically)
- **Consolidated Methods**: Combined duplicate map building methods in `ResultProcessor` into a single generic method
- **Cached Instances**: Cached `DefaultBatcherDiagnostics` instance in `MicroBatcher` to avoid repeated creation
- **Simplified Duration Handling**: Using `System.currentTimeMillis()` instead of `System.nanoTime()` in `BatchFormationStrategy`
- **Code Reduction**: Reduced codebase by ~100+ lines of boilerplate

### Performance Optimizations

- **Pre-sized HashMaps**: Using `HashMap.newHashMap(initialCapacity)` to avoid rehashing overhead
- **Cached Views**: Cached unmodifiable list views in `BatchResult` to eliminate repeated allocations
- **Empty List Optimization**: Using `List.of()` instead of `new ArrayList<>()` for empty lists
- **API Migration**: Migrated from deprecated Micrometer `Timer.percentile()` API to publishing percentiles at timer creation

## Quality Metrics

- ✅ **All Tests Passing**: 306/306 tests pass
- ✅ **Coverage**: 87% instruction coverage, 77% branch coverage
- ✅ **Build Status**: All builds successful
- ✅ **No Breaking Changes**: 100% backward compatible
- ✅ **Documentation**: Complete JavaDoc, updated CHANGELOG and README

## Migration Guide

**No migration required!** Version 0.0.12 is 100% backward compatible. Simply update your dependency version:

### Maven
```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.12</version>
</dependency>
```

### Gradle
```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.12")
}
```

## What's Changed

### Added
- Named virtual threads with "vortex-" prefix for improved observability

### Changed
- Separate executors for dispatch and retry operations
- Code simplification (records, removed boilerplate, consolidated methods)
- Performance optimizations (pre-sized collections, cached views)
- Migrated from deprecated Micrometer API

### Fixed
- Corrected deadline check in `BatchFormationStrategy` (PR review fix)

## Full Changelog

See [CHANGELOG.md](../../CHANGELOG.md) for complete details.

## Contributors

VajraPulse Team

## Links

- [GitHub Repository](https://github.com/happysantoo/vortex)
- [Maven Central](https://search.maven.org/artifact/com.vajrapulse/vortex)
- [Documentation](https://github.com/happysantoo/vortex/blob/main/README.md)

