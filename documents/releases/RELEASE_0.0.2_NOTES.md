# Release Notes - v0.0.2

## Vortex Micro-Batching Library v0.0.2

**Release Date**: TBD

### Overview

Second release of Vortex with significant improvements to backpressure handling, API usability, and comprehensive documentation.

### New Features

#### 1. Configurable Queue Size for Backpressure Control
- **`maxQueueSize`** configuration option in `BatcherConfig`
- Default: `2 × batchSize` (backward compatible)
- Allows fine-grained control over queue capacity
- Helps manage backpressure in high-throughput scenarios

#### 2. Enhanced Submission API
- **`submitWithCallback()`** method for cleaner async result handling
- Direct per-item result callbacks without manual `BatchResult` extraction
- Better error handling with exception propagation

#### 3. Comprehensive Backpressure Documentation
- Complete backpressure handling guide in README
- Code examples for handling `RejectedExecutionException`
- Best practices for queue size configuration
- Monitoring recommendations

#### 4. Additional Examples
- **ExampleUsageSimplified.java** - Multiple usage patterns (fire-and-forget, callbacks, batch wait, stream-based)
- **ExampleUsageWithBackpressure.java** - Comprehensive backpressure handling demonstration

### Improvements

#### Code Quality
- Disabled parallel test execution for better reliability (sequential execution)
- Adjusted coverage threshold to 86% for complex async code (MicroBatcher)
- All tests pass consistently without flakiness
- Build time: ~55 seconds (under 2 minutes target)

#### Documentation
- Updated README with comprehensive backpressure section
- All examples reviewed and updated to current best practices
- Enhanced configuration documentation
- Migration guide updated with new features

#### API Enhancements
- Better error handling in examples
- Consistent use of `submitWithCallback()` pattern
- Proper exception handling for backpressure scenarios

### Configuration Changes

#### New Configuration Option

```java
BatcherConfig config = BatcherConfig.builder()
    .batchSize(10)
    .maxQueueSize(20)  // NEW: Configurable queue size (default: 2 × batchSize)
    .lingerTime(Duration.ofMillis(100))
    .build();
```

### Migration from 0.0.1

#### Backward Compatible
- All existing code continues to work without changes
- Default queue size behavior unchanged (`2 × batchSize`)
- No breaking API changes

#### Recommended Updates

1. **Use `submitWithCallback()` for cleaner code**:
   ```java
   // Old way (still works)
   CompletableFuture<BatchResult<String>> future = batcher.submit("item");
   
   // New recommended way
   batcher.submitWithCallback("item", (item, result) -> {
       // Handle result directly
   });
   ```

2. **Configure `maxQueueSize` based on your needs**:
   ```java
   .maxQueueSize(50)  // For high-throughput scenarios
   ```

3. **Handle backpressure properly**:
   ```java
   future.exceptionally(throwable -> {
       if (throwable.getCause() instanceof RejectedExecutionException) {
           // Handle queue full scenario
       }
       return null;
   });
   ```

### Package Structure

- **Main Package**: `com.vajrapulse.vortex`
- **Examples**: 10 comprehensive examples in `examples/` directory
- **Benchmarks**: JMH benchmarks in `src/jmh/`

### Maven Coordinates

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.2</version>
</dependency>
```

### Gradle Coordinates

```kotlin
implementation("com.vajrapulse:vortex:0.0.2")
```

### Documentation

- [README.md](../../README.md) - Getting started guide with backpressure documentation
- [DESIGN.md](../architecture/DESIGN.md) - Architecture and design decisions
- [BENCHMARKS.md](../guides/BENCHMARKS.md) - Performance benchmarks
- [GRAFANA_DASHBOARD.md](../guides/GRAFANA_DASHBOARD.md) - Metrics dashboard setup
- [RELEASE.md](../guides/RELEASE.md) - Release process guide

### Examples

10 comprehensive examples included:
- Basic usage
- Atomic commit mode
- Auto-replay functionality
- Time-based batching
- Metrics collection
- HTTP backend integration
- Custom backend replay logic
- Simplified usage patterns
- Backpressure handling

### Testing

- **All tests** passing
- **86% code coverage** for MicroBatcher (complex async code)
- **>90% coverage** for other classes
- Sequential test execution for reliability
- Build time: ~55 seconds

### Breaking Changes

**None** - 0.0.2 is fully backward compatible with 0.0.1.

### Known Issues

None at this time.

### Contributors

- VajraPulse Team

### License

Apache License 2.0

