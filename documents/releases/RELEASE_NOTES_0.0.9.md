# Release 0.0.9

**Release Date**: 2025-12-12  
**Version**: 0.0.9

## Overview

Release 0.0.9 introduces a unified submit API, removes the backpressure package for simplicity, and includes comprehensive test suite improvements and documentation updates.

## Key Changes

### Unified Submit API

The API has been simplified with a single `submit(item, callback)` method that replaces both `submitSync()` and `submitWithCallback()`:

- **Returns `ItemResult<T>` immediately** - Success or Failure status available right away
- **Optional callback** - For batch processing results when needed
- **Backward compatible** - `submit(item)` without callback still works
- **Cleaner API** - More intuitive and easier to use

**Migration Guide:**
```java
// Old API (removed)
ItemResult<String> result = batcher.submitSync("item");
batcher.submitWithCallback("item", callback);

// New API (unified)
ItemResult<String> result = batcher.submit("item");
batcher.submit("item", callback);
```

### Removed Backpressure Package

The entire `com.vajrapulse.vortex.backpressure` package has been removed for simplicity:

- Queue rejection is now handled via `queueRejectionThreshold` in `BatcherConfig`
- Simplified rejection logic: queue full → throw exception
- Reduced library complexity and maintenance burden
- Applications can implement their own overflow handling using external queues

**Migration Guide:**
- Remove any imports from `com.vajrapulse.vortex.backpressure.*`
- Use `queueRejectionThreshold` in `BatcherConfig` for queue rejection behavior
- Handle `ItemRejectedException` for rejected items

### Test Suite Improvements

- **Reduced test file size**: From 5,638 lines to 819 lines
- **All 206 tests passing**: Comprehensive test coverage maintained
- **Removed obsolete tests**: Cleaned up tests for removed APIs
- **Improved test organization**: Better structure and maintainability

### JMH Benchmarks

- **Updated all benchmarks**: Now use unified `submit()` API
- **Removed backpressure code**: Simplified benchmark implementations
- **All benchmarks compile and run successfully**

### Documentation

- **Updated examples**: All examples now use unified `submit()` API
- **GitHub Actions**: Added automated JMH benchmark workflow
  - Runs benchmarks on merge to main
  - Publishes HTML reports to GitHub Pages
  - Uploads artifacts for 90 days
- **Copilot instructions**: Added comprehensive `.github/copilot-instructions.md`
- **Cursor rules**: Enhanced `.cursorrules` with detailed testing and code review guidelines

## Breaking Changes

### Removed APIs

- `MicroBatcher.submitSync(T item)` - Use `submit(item)` instead
- `MicroBatcher.submitWithCallback(T item, ItemCallback<T> callback)` - Use `submit(item, callback)` instead
- Entire `com.vajrapulse.vortex.backpressure` package - Use `queueRejectionThreshold` instead

### Package Reorganization

Several classes have been moved to new packages for better organization:

- `com.vajrapulse.vortex.BatchSizePreset` → `com.vajrapulse.vortex.config.BatchSizePreset`
- `com.vajrapulse.vortex.BatcherHealth` → `com.vajrapulse.vortex.health.BatcherHealth`
- `com.vajrapulse.vortex.BatcherDiagnostics` → `com.vajrapulse.vortex.health.BatcherDiagnostics`
- `com.vajrapulse.vortex.MetricsManager` → `com.vajrapulse.vortex.metrics.MetricsManager`
- `com.vajrapulse.vortex.MetricsProvider` → `com.vajrapulse.vortex.metrics.MetricsProvider`
- `com.vajrapulse.vortex.RetryManager` → `com.vajrapulse.vortex.internal.RetryManager`
- `com.vajrapulse.vortex.ResultProcessor` → `com.vajrapulse.vortex.internal.ResultProcessor`
- `com.vajrapulse.vortex.PendingRequest` → `com.vajrapulse.vortex.internal.PendingRequest`
- `com.vajrapulse.vortex.BatchResult` → `com.vajrapulse.vortex.results.BatchResult`
- `com.vajrapulse.vortex.ItemResult` → `com.vajrapulse.vortex.results.ItemResult`
- `com.vajrapulse.vortex.SuccessEvent` → `com.vajrapulse.vortex.results.SuccessEvent`
- `com.vajrapulse.vortex.FailureEvent` → `com.vajrapulse.vortex.results.FailureEvent`

**Note**: Most of these are internal classes. Public APIs remain unchanged.

## Improvements

- **Code cleanup**: Removed ~4,755 lines of unused code
- **Test coverage**: Maintained >90% line coverage, >80% instruction coverage
- **Build improvements**: Fixed all coverage violations
- **Documentation**: Enhanced testing and code review guidelines

## Dependencies

No dependency changes in this release.

## Upgrade Instructions

1. **Update imports** if using internal classes (see package reorganization above)
2. **Replace `submitSync()` calls** with `submit()`
3. **Replace `submitWithCallback()` calls** with `submit(item, callback)`
4. **Remove backpressure package imports** and related code
5. **Update tests** to use new unified API

## Full Changelog

See [CHANGELOG.md](../../CHANGELOG.md) for complete details.

## Maven Coordinates

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.9</version>
</dependency>
```

## Gradle Coordinates

```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.9")
}
```

## Links

- [GitHub Repository](https://github.com/happysantoo/vortex)
- [Maven Central](https://search.maven.org/artifact/com.vajrapulse/vortex)
- [Documentation](https://github.com/happysantoo/vortex/blob/main/README.md)

