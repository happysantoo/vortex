# Release 0.0.10: API Simplification and Documentation

**Release Date**: 2025-01-XX  
**Version**: 0.0.10

## Overview

Version 0.0.10 focuses on API simplification, removing unnecessary complexity, and providing comprehensive documentation. This release makes the library easier to use while maintaining all core functionality.

## Major Changes

### API Simplification

- **Removed Factory Methods**: Eliminated static factory methods (`forHighThroughput()`, `forLowLatency()`, `forBalanced()`, `forResilient()`) in favor of constructors with `BatcherConfig` presets
- **Removed Dynamic Configuration**: Eliminated runtime configuration updates (`updateBatchSize()`, `updateLingerTime()`) - configuration is now immutable
- **Simplified Internal State**: Removed redundant internal fields, reducing code complexity by 260 lines

### New Features

- **Configuration Presets**: Added preset factory methods to `BatcherConfig` for common scenarios:
  - `highThroughputPreset()` - Optimized for maximum throughput
  - `lowLatencyPreset()` - Optimized for minimal latency
  - `balancedPreset()` - Balanced latency and throughput
  - `resilientPreset(Predicate<Throwable>)` - Optimized for resilience with retry

- **Comprehensive User Guide**: Added complete user documentation (`documents/guides/USER_GUIDE.md`) covering:
  - Detailed usage patterns
  - Exception handling strategies
  - Backpressure management
  - Synchronous vs asynchronous usage
  - Best practices and troubleshooting

## Breaking Changes

### Factory Methods Removed

**Before:**
```java
MicroBatcher<String> batcher = MicroBatcher.forHighThroughput(backend, registry);
```

**After:**
```java
MicroBatcher<String> batcher = new MicroBatcher<>(
    backend, 
    BatcherConfig.highThroughputPreset(), 
    registry
);
```

### Dynamic Configuration Removed

**Before:**
```java
batcher.updateBatchSize(20);
batcher.updateLingerTime(Duration.ofMillis(200));
```

**After:**
```java
// Configuration is immutable - create new BatcherConfig if needed
BatcherConfig newConfig = BatcherConfig.builder()
    .batchSize(20)
    .lingerTime(Duration.ofMillis(200))
    .build();
```

## Improvements

- **Code Reduction**: Reduced `MicroBatcher` from 1,204 to 944 lines (260 lines removed)
- **Test Coverage**: Increased to 81% for `MicroBatcher` class (from 77%)
- **Documentation**: Completely rewritten README with current API and comprehensive User Guide

## Migration Guide

1. Replace factory methods with constructors using presets:
   ```java
   // Old
   MicroBatcher<String> batcher = MicroBatcher.forHighThroughput(backend, registry);
   
   // New
   MicroBatcher<String> batcher = new MicroBatcher<>(
       backend, 
       BatcherConfig.highThroughputPreset(), 
       registry
   );
   ```

2. Remove any calls to `updateBatchSize()` or `updateLingerTime()`

3. If you need different configuration, create a new `BatcherConfig` instance

4. Review the new [User Guide](documents/guides/USER_GUIDE.md) for detailed usage patterns

## Documentation

- **README**: Completely rewritten to reflect current API
- **User Guide**: New comprehensive guide covering all features
- **CHANGELOG**: Updated with detailed migration instructions

## Testing

- All 252 tests passing
- Coverage verification passing (81% for MicroBatcher)
- No breaking changes to core functionality

## Dependencies

No dependency changes in this release.

## Contributors

Thank you to all contributors who helped with this release!

---

For detailed migration instructions and examples, see the [User Guide](documents/guides/USER_GUIDE.md) and [CHANGELOG.md](../CHANGELOG.md).

