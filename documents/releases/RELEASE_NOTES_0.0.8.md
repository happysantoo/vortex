# Release Notes - Vortex 0.0.8

**Release Date**: December 9, 2025

Version 0.0.8 includes significant improvements focused on code simplification, modern Java features, and enhanced observability.

## 🎉 Highlights

- **New Tracing Hooks**: LoggingTracingHook and MicrometerTracingHook for better observability
- **Unified Exception Handling**: All rejections now throw `BackpressureException` for simpler error handling
- **Code Modernization**: Converted `PendingRequest` to Java Record, refactored `BatcherHealth`
- **Simplified API**: Removed overflow strategy functionality (application concern)
- **Better Performance**: Direct Micrometer Tracing API integration replaces reflection-based approach

## ✨ Added

### LoggingTracingHook
New SLF4J-based tracing hook for simple log-based observability:
- Emits DEBUG logs for successful events (submit, batch dispatch start, batch dispatch success)
- Emits WARN logs for retry events
- Emits ERROR logs for failure events (batch dispatch failure)
- Uses standard SLF4J parameterized logging (no String.format)
- No additional dependencies required (SLF4J already included)

### Micrometer Tracing Integration
Direct integration with Micrometer Tracing API:
- Replaced reflection-based OpenTelemetry implementation
- Direct API usage improves performance and maintainability
- Works with any Micrometer Tracing backend (OpenTelemetry, Zipkin, Brave, etc.)
- Added `micrometer-tracing` as a dependency

## 🔄 Changed

### Exception Unification
Unified all rejection exceptions into `BackpressureException`:
- `BackpressureException` is now the single exception type for all rejection scenarios
- Queue full, concurrent limit, and backpressure rejections all throw `BackpressureException`
- Simplified application-side exception handling
- Rich metadata (backpressure level, threshold, source) available in exception
- Changed from extending `RejectedExecutionException` to `RuntimeException` (no backward compatibility concerns)

**Migration**: Update exception handling code:
```java
// Before (0.0.7)
if (throwable instanceof RejectedExecutionException) { ... }

// After (0.0.8)
if (throwable instanceof BackpressureException) { ... }
```

### PendingRequest Modernization
Converted `PendingRequest` to Java Record:
- More concise and immutable
- Leverages modern Java 21 features
- Maintains backward compatibility with convenience getters

### BatcherHealth Refactoring
Improved organization and maintainability:
- Extracted `HealthStatus` enum to separate file
- Extracted `HealthInfo` record to separate file
- Reduced code duplication by consolidating common logic
- Replaced magic numbers with named constants
- More modular and testable design

## 🗑️ Removed

### Overflow Strategy
Removed overflow functionality from library:
- Removed `OverflowStrategy`, `OverflowStorage`, `InMemoryOverflowStorage`, `LifecycleAwareStrategy`
- Overflow handling is now an application concern
- Library focuses on rejecting items when capacity is exceeded
- Applications can implement their own overflow handling using `RejectStrategy` and external queues
- Simplified library API and reduced complexity

**Migration**: If you were using `OverflowStrategy`, implement overflow handling in your application:
```java
// Application-level overflow handling
Queue<Item> overflowQueue = new LinkedBlockingQueue<>();

try {
    batcher.submit(item);
} catch (BackpressureException e) {
    overflowQueue.offer(item); // Store for later replay
}
```

### Reflection-based OpenTelemetry
Removed `OpenTelemetryTracingHook`:
- Replaced with direct `MicrometerTracingHook` using Micrometer Tracing API
- Eliminates brittle reflection-based implementation
- Better performance and maintainability

**Migration**: Use `MicrometerTracingHook` instead:
```java
// Before (0.0.7)
OpenTelemetryTracingHook hook = new OpenTelemetryTracingHook();

// After (0.0.8)
Tracer tracer = ...; // From your Micrometer Tracing setup
MicrometerTracingHook hook = new MicrometerTracingHook(tracer);
```

## 📚 Documentation

- Added comprehensive analysis documents for design decisions
- Updated examples to reflect new API
- Updated README with unified exception handling patterns

## 🔧 Technical Details

### Maven Dependency
```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.8</version>
</dependency>
```

### Gradle Dependency
```kotlin
dependencies {
    implementation("com.vajrapulse:vortex:0.0.8")
}
```

## 🐛 Bug Fixes

- Fixed backpressure package simplification (removed unused `getThreshold()` method)
- Improved exception handling consistency

## 📊 Statistics

- **Files Changed**: 50 files
- **Lines Added**: 3,957
- **Lines Removed**: 3,554
- **Net Change**: +403 lines

## 🙏 Acknowledgments

Thank you for using Vortex! This release focuses on simplicity and maintainability while adding powerful observability features.

## 🔗 Links

- [Full Changelog](https://github.com/happysantoo/vortex/blob/main/CHANGELOG.md)
- [Documentation](https://github.com/happysantoo/vortex/blob/main/README.md)
- [Issues](https://github.com/happysantoo/vortex/issues)

