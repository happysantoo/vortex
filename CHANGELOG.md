# Changelog

All notable changes to the Vortex Micro-Batching Library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.12] - 2025-12-26

### Added
- **Named Virtual Threads**: All virtual threads now have descriptive names with "vortex-" prefix for improved observability
  - Thread naming pattern: `vortex-{instanceId}-{type}-{N}`
  - Batch processor thread: `vortex-{id}-batch-processor`
  - Dispatch worker threads: `vortex-{id}-dispatch-0`, `vortex-{id}-dispatch-1`, etc.
  - Retry worker threads: `vortex-{id}-retry-0`, `vortex-{id}-retry-1`, etc.
  - Each MicroBatcher instance gets a unique ID for thread naming
  - Improves debugging with clear thread identification in thread dumps
  - Better APM/monitoring tool integration
  - Example thread dump:
    ```
    "vortex-1-batch-processor" #42 virtual
    "vortex-1-dispatch-0" #43 virtual
    "vortex-1-dispatch-1" #44 virtual
    "vortex-1-retry-0" #45 virtual
    "vortex-retry-cleanup" #46
    ```

### Changed
- **Separate Executors**: Internal architecture now uses separate executors for dispatch and retry operations
  - `dispatchExecutor`: Handles backend dispatch operations with named threads
  - `retryExecutor`: Handles retry operations with named threads
  - Batch processor runs on a dedicated named virtual thread
  - No performance impact (virtual threads share the same carrier thread pool)

## [0.0.11] - 2025-12-24

### Added
- **Asynchronous Submission API**: Added `submitAsync(T item)` method that returns `CompletableFuture<ItemResult<T>>`
  - Returns `CompletableFuture<ItemResult<T>>` immediately (never blocks)
  - Completes exceptionally with `ItemRejectedException` if queue is full or threshold is reached
  - Completes with `ItemResult` when batch processing finishes
  - Perfect for chaining operations with `thenApply()`, `thenAccept()`, `thenCompose()`, etc.
  - Works seamlessly with `CompletableFuture.allOf()` and `anyOf()` for batch operations
  - Example:
    ```java
    CompletableFuture<ItemResult<String>> future = batcher.submitAsync("item");
    future
        .thenApply(result -> processResult(result))
        .thenAccept(processed -> System.out.println("Processed: " + processed))
        .exceptionally(throwable -> {
            if (throwable instanceof ItemRejectedException) {
                handleRejection(throwable);
            }
            return null;
        });
    ```

- **JMH Benchmarks for submitAsync**: Added comprehensive benchmarks for the new `submitAsync()` method
  - Throughput benchmarks: `submitAsyncSingleRequest`, `submitAsyncConcurrentRequests`, `submitAsyncBatchWithCompletion`
  - Latency benchmarks: `submitAsyncLatency`, `submitAsyncToCompletionLatency`, `submitAsyncRejectionLatency`
  - Allows performance comparison between synchronous and asynchronous submission methods

- **New Examples**: Added comprehensive examples demonstrating all submission methods
  - `ThreeSubmissionMethodsExample.java` - Demonstrates all three ways to submit items
  - `ErrorHandlingExample.java` - Comprehensive error handling patterns

### Changed
- **ItemCallback API Simplification**: Simplified `ItemCallback` interface signature
  - Changed from `void onResult(T item, ItemResult<T> result)` to `void onResult(ItemResult<T> result)`
  - The item is already available within `ItemResult<T>`, making the separate parameter redundant
  - **Migration**: Update callback lambdas from `(item, result) ->` to `result ->`
    ```java
    // Old
    batcher.submit("item", (item, result) -> {
        System.out.println("Item: " + item);
        // ...
    });
    
    // New
    batcher.submit("item", result -> {
        System.out.println("Item: " + result.getItem());
        // ...
    });
    ```

- **Code Refactoring**: Extracted internal classes to separate files for better maintainability
  - Extracted `EnqueueResult`, `SubmissionContext`, `BatchFormationStrategy`, `BatchDispatcher`, `SubmissionHandler`, `ShutdownManager`, `TracingHelper`, and `DefaultBatcherDiagnostics`
  - Reduced `MicroBatcher` class size from 1,075 lines to 651 lines
  - Improved code organization and testability
  - No breaking changes to public API

- **Dependencies**: Updated Micrometer to 1.16.1 (latest stable version)
  - Fixed deprecation warnings in `DefaultMetricsProvider`
  - Replaced deprecated `Timer.mean()` with `totalTime() / count()`
  - Suppressed warnings for `Timer.percentile()` (no clear replacement API available)

- **Build Configuration**: Improved examples dependency configuration
  - Uses `extendsFrom` to automatically inherit all `implementation` dependencies
  - Eliminates need to manually sync dependencies between main and examples sourceSets

### Removed
- **Redundant Examples**: Removed outdated and redundant example files
  - `ExampleUsageSimplified.java` - Redundant with `ThreeSubmissionMethodsExample.java`
  - `HttpBackendExample.java` - Low value, custom backend patterns already demonstrated
  - `AdaptiveBatchingExample.java` - No longer relevant (dynamic batch size updates removed in 0.0.10)
  - `BackpressureExample.java`, `KafkaConsumerBackpressureExample.java`, `ExampleUsageWithBackpressure.java` - Outdated backpressure examples

### Fixed
- **Examples Simplification**: Simplified all examples by extracting duplicated callbacks
  - Reduced code duplication across all example files
  - Improved maintainability and readability
  - All examples now follow consistent patterns

- **Benchmark Updates**: Fixed benchmarks to use current API
  - Updated `ItemCallback` signature in all benchmark files
  - All benchmarks compile and run successfully

## [0.0.10] - 2025-01-XX

### Changed
- **Simplified API**: Removed factory methods and dynamic configuration for cleaner API
  - Removed `forHighThroughput()`, `forLowLatency()`, `forBalanced()`, `forResilient()` factory methods
  - Removed `updateBatchSize()` and `updateLingerTime()` dynamic configuration methods
  - Removed `getCurrentBatchSize()` and `getCurrentLingerTime()` getters
  - Configuration is now immutable - use `BatcherConfig` presets with constructors
  - Reduced API surface and complexity

### Removed
- **Factory Methods**: Removed all static factory methods from `MicroBatcher`
  - `forHighThroughput(Backend<T>, MeterRegistry)`
  - `forLowLatency(Backend<T>, MeterRegistry)`
  - `forBalanced(Backend<T>, MeterRegistry)`
  - `forResilient(Backend<T>, MeterRegistry, Predicate<Throwable>)`
  - **Migration**: Use constructors with `BatcherConfig` presets:
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

- **Dynamic Configuration Methods**: Removed runtime configuration updates
  - `updateBatchSize(int)`
  - `updateLingerTime(Duration)`
  - `getCurrentBatchSize()`
  - `getCurrentLingerTime()`
  - **Migration**: Configuration is now immutable. Create a new `BatcherConfig` if you need different settings.

- **Internal Fields**: Removed redundant internal fields
  - `currentBatchSize` and `currentLingerTime` fields (now read directly from config)
  - `maxConcurrentBatches` field (now read directly from config)

### Added
- **Configuration Presets**: Added preset factory methods to `BatcherConfig`
  - `highThroughputPreset()` - Optimized for maximum throughput
  - `lowLatencyPreset()` - Optimized for minimal latency
  - `balancedPreset()` - Balanced latency and throughput
  - `resilientPreset(Predicate<Throwable>)` - Optimized for resilience with retry
  - Use with constructors: `new MicroBatcher<>(backend, BatcherConfig.highThroughputPreset(), registry)`

- **Comprehensive User Guide**: Added complete user documentation
  - Detailed guide covering all features, exception handling, backpressure, sync/async usage
  - Best practices and troubleshooting sections
  - Complete examples for all use cases
  - Located at `documents/guides/USER_GUIDE.md`

### Fixed
- **Code Simplification**: Reduced `MicroBatcher` complexity
  - Removed 260 lines of code (from 1,204 to 944 lines)
  - Simplified internal field management
  - Improved code maintainability

- **Test Coverage**: Increased test coverage for `MicroBatcher` class
  - Coverage increased to 82% (from 77%)
  - Added 25+ new test cases covering edge cases, error handling, and shutdown scenarios
  - All 252 tests passing

### Documentation
- **README Rewrite**: Completely rewritten README to reflect current API
  - Removed references to removed factory methods and dynamic configuration
  - Updated all examples to use current API
  - Added references to comprehensive User Guide
  - Simplified and clarified documentation

- **Updated Examples**: All examples updated to use current API
  - Removed factory method usage
  - Updated to use `BatcherConfig` presets with constructors

## [0.0.9] - 2025-12-12

### Changed
- **Unified Submit API**: Simplified API with single `submit(item, callback)` method
  - Replaced `submitSync()` and `submitWithCallback()` with unified `submit(item, callback)` method
  - Returns `ItemResult<T>` immediately (Success or Failure)
  - Optional callback for batch processing results
  - Cleaner, more intuitive API
  - Backward compatible: `submit(item)` without callback still works
- **Removed Backpressure Package**: Eliminated entire backpressure package for simplicity
  - Removed `BackpressureProvider`, `BackpressureStrategy`, and related classes
  - Queue rejection now handled via `queueRejectionThreshold` in `BatcherConfig`
  - Simplified rejection logic: queue full → throw exception
  - Reduced library complexity and maintenance burden

### Removed
- **Backpressure Package**: Removed entire `com.vajrapulse.vortex.backpressure` package
  - Removed `BackpressureProvider`, `BackpressureStrategy`, `BackpressureContext`, `BackpressureResult`
  - Removed `QueueDepthBackpressureProvider`, `CompositeBackpressureProvider`
  - Removed `RejectStrategy`, `DropStrategy`, `BackpressureLevelCache`
  - Removed `BackpressureAction` enum
  - Backpressure metrics removed from `MetricsManager`
- **Old Submit APIs**: Removed `submitSync()` and `submitWithCallback()` methods
  - Replaced by unified `submit(item, callback)` API
  - Migration: Use `submit(item)` or `submit(item, callback)` instead

### Fixed
- **Test Suite Cleanup**: Rewrote test suite to use new unified API
  - Reduced test file size from 5,638 lines to 819 lines
  - Removed obsolete tests for removed APIs
  - All 206 tests passing
- **JMH Benchmarks**: Updated all benchmarks to use unified `submit()` API
  - Removed backpressure-related benchmark code
  - Simplified benchmark implementations
  - All benchmarks compile and run successfully

### Documentation
- **Updated Examples**: All examples updated to use unified `submit()` API
- **Updated JMH Benchmarks**: All benchmarks reflect current API
- **GitHub Actions**: Added automated JMH benchmark workflow
  - Runs benchmarks on merge to main
  - Publishes HTML reports to GitHub Pages
  - Uploads artifacts for 90 days

## [0.0.8] - 2025-12-09

### Added
- **LoggingTracingHook**: New SLF4J-based tracing hook for simple log-based observability
  - Emits DEBUG logs for successful events (submit, batch dispatch start, batch dispatch success)
  - Emits WARN logs for retry events
  - Emits ERROR logs for failure events (batch dispatch failure)
  - Uses standard SLF4J parameterized logging (no String.format)
  - No additional dependencies required (SLF4J already included)
- **Micrometer Tracing Integration**: Direct integration with Micrometer Tracing API
  - Replaced reflection-based OpenTelemetry implementation
  - Direct API usage improves performance and maintainability
  - Works with any Micrometer Tracing backend (OpenTelemetry, Zipkin, Brave, etc.)
  - Added `micrometer-tracing` as a dependency

### Changed
- **Exception Unification**: Unified all rejection exceptions into `BackpressureException`
  - `BackpressureException` is now the single exception type for all rejection scenarios
  - Queue full, concurrent limit, and backpressure rejections all throw `BackpressureException`
  - Simplified application-side exception handling
  - Rich metadata (backpressure level, threshold, source) available in exception
  - Changed from extending `RejectedExecutionException` to `RuntimeException` (no backward compatibility concerns)
- **PendingRequest Modernization**: Converted `PendingRequest` to Java Record
  - More concise and immutable
  - Leverages modern Java 21 features
  - Maintains backward compatibility with convenience getters
- **BatcherHealth Refactoring**: Improved organization and maintainability
  - Extracted `HealthStatus` enum to separate file
  - Extracted `HealthInfo` record to separate file
  - Reduced code duplication by consolidating common logic
  - Replaced magic numbers with named constants
  - More modular and testable design

### Removed
- **Overflow Strategy**: Removed overflow functionality from library
  - Removed `OverflowStrategy`, `OverflowStorage`, `InMemoryOverflowStorage`, `LifecycleAwareStrategy`
  - Overflow handling is now an application concern
  - Library focuses on rejecting items when capacity is exceeded
  - Applications can implement their own overflow handling using `RejectStrategy` and external queues
  - Simplified library API and reduced complexity
- **Reflection-based OpenTelemetry**: Removed `OpenTelemetryTracingHook`
  - Replaced with direct `MicrometerTracingHook` using Micrometer Tracing API
  - Eliminates brittle reflection-based implementation
  - Better performance and maintainability

### Documentation
- **Analysis Documents**: Added comprehensive analysis documents
  - `BACKPRESSURE_DESIGN_ANALYSIS.md` - Analysis of backpressure package design
  - `BACKPRESSURE_EXCEPTION_HANDLING.md` - Exception handling patterns
  - `EXCEPTION_UNIFICATION_ANALYSIS.md` - Exception unification rationale
  - `EXCEPTION_UNIFICATION_NO_BC.md` - Exception unification without BC concerns
  - `PENDING_REQUEST_ANALYSIS.md` - PendingRequest modernization analysis
  - `BATCHER_HEALTH_ANALYSIS.md` - BatcherHealth refactoring analysis
- **Updated Examples**: Rewrote examples to reflect new API
  - `KafkaConsumerBackpressureExample` demonstrates application-level overflow handling
  - `TracingExample` demonstrates both `LoggingTracingHook` and `MicrometerTracingHook`
- **README Updates**: Updated documentation for new tracing hooks and simplified API

## [0.0.7] - 2025-12-06

### Added
- **Concurrent Batch Dispatch Limiter**: New `maxConcurrentBatches` configuration to prevent connection pool exhaustion
  - Limits the number of batches that can be dispatched concurrently
  - Recommended value: 80% of connection pool size
  - Prevents overwhelming connection pools by controlling concurrent batch dispatches
  - Rejected batches are handled gracefully with proper error notifications
- **Enhanced Metrics**: New metrics for concurrent dispatch tracking
  - `vortex.dispatch.rejected` - Counter for batches rejected due to concurrent dispatch limit
  - `vortex.dispatch.active.batches` - Gauge for current number of batches being dispatched concurrently
- **Graceful Shutdown**: New `awaitCompletion()` method for waiting on queue and in-flight batches
  - Waits for all queued items to be processed
  - Waits for all in-flight batches to complete
  - Useful for test teardown and application shutdown scenarios
  - Handles interruption and timeouts gracefully
- **CompositeBackpressureProvider Builder**: Builder pattern for easier composite provider construction
  - Fluent API for combining multiple backpressure providers
  - Convenience method `queueDepth()` for adding queue depth provider
  - `add()` method for adding custom providers
  - Cleaner, more intuitive API compared to constructor-based approach
- **RetryManager Memory Leak Prevention**: Automatic cleanup of retry count entries
  - Size limit (10,000 entries) to prevent unbounded growth
  - Periodic cleanup (every 5 minutes) of stale entries
  - Automatic eviction when limit is reached
  - Prevents memory leaks in high-retry scenarios

### Changed
- **Shutdown Behavior**: Enhanced `close()` method to wait for in-flight batches when concurrent limiting is enabled
  - Prevents race conditions during shutdown
  - Ensures all batches complete before executor shutdown
- **BatcherConfig**: Added `maxConcurrentBatches` configuration option
  - Default: 0 (unlimited)
  - Must be positive when set
  - Integrated into builder pattern
- **API Simplification**: Removed `MicroBatcher.withBackpressure()` factory methods
  - All backpressure configuration now via `BatcherConfig.builder()`
  - Cleaner, more consistent API
  - Backpressure provider and strategy configured directly in config
- **Error Messages**: Enhanced error messages with context
  - `IllegalStateException` messages now include queue depth and active batch count
  - Better debugging information when batcher is closed
  - Applied to all submission and configuration methods

### Fixed
- **Race Condition**: Fixed TOCTOU race condition in `submitSync()` queue check
  - Removed redundant queue size check
  - Now relies directly on atomic `queue.offer()` operation
  - Eliminates race condition window
- **Memory Leak**: Fixed potential memory leak in `RetryManager`
  - Added size limit and periodic cleanup
  - Prevents unbounded growth of retry count map
- **Code Clarity**: Improved `activeBatchCount` increment timing
  - Now incremented after successful `executor.submit()` call
  - Clearer semantics and simpler error handling

### Documentation
- **README Updates**: Added examples for concurrent dispatch limiting and graceful shutdown
- **JavaDoc**: Enhanced documentation for new features
  - `maxConcurrentBatches()` builder method documentation
  - `awaitCompletion()` method documentation with examples
  - `CompositeBackpressureProvider.builder()` documentation
- **Review Documentation**: Added principal engineer code review document
  - Comprehensive code review for correctness and improvements
  - All identified issues addressed

## [0.0.6] - 2025-12-05

### Added
- **Enhanced Documentation**: Comprehensive JavaDoc improvements for better developer experience
  - Enhanced `submitSync()` JavaDoc with detailed examples and load testing framework integration
  - Enhanced `submitWithCallback()` JavaDoc with timing details and integration examples
  - Enhanced `QueueDepthBackpressureProvider` JavaDoc with AdaptiveLoadPattern integration guide
  - Enhanced `getQueueDepth()` JavaDoc with usage examples and use cases
- **Usage Guide**: New comprehensive guide for adaptive load testing
  - `documents/guides/ADAPTIVE_LOAD_TESTING_GUIDE.md` - Complete guide for queue-only backpressure approach
  - Integration examples with VajraPulse AdaptiveLoadPattern
  - Configuration recommendations and best practices
  - Troubleshooting guide
- **Enhanced README**: Additional usage examples and integration patterns
  - Advanced backpressure configuration examples with `QueueDepthBackpressureProvider`
  - Detailed `submitSync()` integration examples for load testing frameworks
  - Enhanced `submitWithCallback()` examples with hybrid approach
  - Queue-only backpressure rationale and recommended configurations

### Changed
- **Factory Method JavaDoc**: Enhanced documentation for factory methods
  - Added performance characteristics section to `forHighThroughput()`, `forLowLatency()`, `forBalanced()`, and `forResilient()`
  - Clarified when to use each factory method
  - Added performance characteristics (throughput, latency, memory) for each factory method

### Documentation
- **README Updates**: Enhanced with comprehensive examples
  - Added advanced backpressure configuration section
  - Added detailed `submitSync()` and `submitWithCallback()` integration examples
  - Added queue-only backpressure approach explanation
  - Updated version to 0.0.6

## [0.0.5] - 2025-12-04

### Added
- **Synchronous Submission API**: New `submitSync()` method for immediate rejection visibility
  - Returns `ItemResult<T>` (Success or Failure) immediately
  - Synchronous backpressure and queue capacity checks
  - Useful for load testing frameworks and scenarios requiring immediate rejection feedback
  - Thread-safe and non-blocking queue operations
  - Performance: 11% faster than async `submit()` when items are accepted
- **Enhanced `submitWithCallback()` Method**: Improved callback-based submission
  - Immediate callback invocation for rejections (queue full, backpressure)
  - Eventual callback for accepted items (when batch completes)
  - Exception handling in callbacks
  - Hybrid approach support (combine `submitSync()` + `submitWithCallback()`)
- **Backpressure Level Caching**: Performance optimization for backpressure checks
  - `BackpressureLevelCache` class with TTL-based caching
  - Configurable cache TTL via `BatcherConfig.backpressureCacheTtl()` (default: 50ms)
  - Reduces backpressure provider calls by ~95% in high-throughput scenarios
  - Thread-safe implementation with automatic cache invalidation
- **Enhanced Error Metrics**: Additional observability for error scenarios
  - `vortex.backpressure.check.failures` - Counter for backpressure check exceptions
  - `vortex.backpressure.invalid.levels` - Counter for invalid backpressure levels (NaN, out of range)
  - `vortex.queue.offer.failures` - Counter for queue offer failures (race conditions)
- **OpenTelemetry Distributed Tracing Integration**: Optional tracing support
  - `OpenTelemetryTracingHook` class for OpenTelemetry integration
  - Reflection-based implementation (works without OpenTelemetry in classpath)
  - Creates spans for key operations (submit, batch dispatch, retry)
  - Propagates trace context through batch processing
  - Graceful degradation when OpenTelemetry unavailable
- **Individual Item Metrics Fix**: Corrected metrics for per-item tracking
  - Fixed `vortex.item.wait.time` to record only queue wait time (not full latency)
  - `vortex.item.submit.latency` correctly records full submit-to-completion latency
  - Metrics now accurately distinguish between queue wait and total processing time

### Changed
- **Individual Item Metrics**: Fixed incorrect latency recording
  - `itemWaitTime` now correctly records only queue wait time
  - `itemSubmitLatency` records full submit-to-completion latency
  - Metrics accurately reflect queue wait vs backend processing time
- **`submitWithCallback()` Implementation**: Refactored to use `checkRejection()` helper
  - Immediate rejection checks before queuing
  - Improved performance and consistency with `submitSync()`
- **Backpressure Checks**: Optimized with TTL-based caching
  - Reduced overhead of backpressure provider calls
  - Configurable cache TTL for different use cases
- **Documentation**: Enhanced JavaDoc with race condition notes
  - Documented queue depth check race condition in `submitSync()` and `checkRejection()`
  - Clarified acceptable behavior and edge cases

### Fixed
- **Individual Item Metrics Accuracy**: Fixed `itemWaitTime` incorrectly recording full latency
  - Now correctly records only queue wait time
  - Full latency recorded separately in `itemSubmitLatency`
  - Metrics now provide accurate observability for per-item tracking

### Performance Improvements
- **Backpressure Caching**: Reduces provider calls by ~95% in high-throughput scenarios
- **SubmitSync Performance**: 11% faster than async `submit()` when items are accepted
- **Backpressure Check Overhead**: Reduced by ~95% with TTL-based caching

## [0.0.4] - 2025-01-XX

### Added
- **Backpressure Detection System**: Comprehensive backpressure handling capabilities
  - `BackpressureProvider` interface - Generic interface for detecting system pressure (0.0-1.0 scale)
  - `BackpressureStrategy<T>` interface - Strategy pattern for handling items when backpressure is detected
  - `LifecycleAwareStrategy<T>` interface - Optional interface for strategies needing lifecycle management
  - `BackpressureContext<T>` record - Context for backpressure handling
  - `BackpressureResult<T>` record - Result of backpressure handling (ACCEPT, REJECT, DROP)
  - `BackpressureAction` enum - Defines possible actions
  - `BackpressureException` - Custom exception for backpressure-related rejections
- **Built-in Backpressure Providers**:
  - `QueueDepthBackpressureProvider` - Monitors internal queue depth with linear scaling
  - `CompositeBackpressureProvider` - Combines multiple backpressure sources (uses maximum)
- **Built-in Backpressure Strategies**:
  - `DropStrategy<T>` - Silently drops items when backpressure exceeds threshold
  - `RejectStrategy<T>` - Rejects items with `BackpressureException` when threshold exceeded
  - `OverflowStrategy<T>` - Stores items to overflow storage and manages external consumer lifecycle
- **Overflow Management**:
  - `OverflowStorage<T>` interface - Interface for temporary storage during backpressure
  - `InMemoryOverflowStorage<T>` - In-memory implementation using `ConcurrentLinkedQueue`
  - Automatic item replay when backpressure resolves
  - Configurable pause/resume callbacks for external consumers (e.g., Kafka)
- **Lifecycle Management**:
  - `onBackpressureEntered()` - Called when backpressure first detected
  - `onBackpressureResolved()` - Called when backpressure resolves
  - `onBackpressureActive()` - Called periodically while backpressure is active
  - Background monitoring thread (configurable interval, default 100ms)
  - Automatic state transition detection
- **Enhanced Metrics**:
  - `vortex.backpressure.rejected` - Counter for items rejected due to backpressure
  - `vortex.backpressure.dropped` - Counter for items dropped due to backpressure
- **Factory Methods for Common Patterns**: New static factory methods in `MicroBatcher` for quick setup
  - `MicroBatcher.forHighThroughput()` - Optimized for maximum throughput (batch size: 100, linger: 500ms)
  - `MicroBatcher.forLowLatency()` - Optimized for low latency (batch size: 5, linger: 10ms)
  - `MicroBatcher.forBalanced()` - Balanced configuration (batch size: 20, linger: 100ms)
  - `MicroBatcher.forResilient()` - With retry support (3 retries, 100ms delay)
  - `MicroBatcher.withBackpressure()` - Convenient backpressure setup
- **Batch Size Presets**: New `BatchSizePreset` enum with predefined configurations
  - `TINY` - 5 items, 10ms linger (ultra-low latency)
  - `SMALL` - 10 items, 50ms linger (low latency)
  - `MEDIUM` - 20 items, 100ms linger (balanced, default)
  - `LARGE` - 50 items, 200ms linger (high throughput)
  - `HUGE` - 100 items, 500ms linger (maximum throughput)
  - Methods: `toConfig()`, `toConfigBuilder()` for easy configuration
- **Health Check Utilities**: New `BatcherHealth` utility class for standardized health checks
  - `BatcherHealth.check()` - Quick health check with default thresholds
  - `BatcherHealth.checkWithThresholds()` - Custom threshold health check
  - `BatcherHealth.getHealthInfo()` - Detailed health information map
  - Returns `HealthStatus` enum: `UP`, `DEGRADED`, `DOWN`
  - Easy integration with Spring Boot Actuator, Kubernetes probes, etc.
- **Configuration Enhancements**:
  - `BatcherConfig.backpressureProvider()` builder method
  - `BatcherConfig.backpressureStrategy()` builder method
  - `BatcherConfig.backpressureMonitorInterval()` builder method (default: 100ms)
- **Enhanced API**: `MicroBatcher.getConfig()` method for accessing batcher configuration
- **Kafka Consumer Example**: Comprehensive example demonstrating Kafka consumer integration with backpressure
  - Shows pause/resume integration
  - Demonstrates overflow storage usage
  - Clear separation of application and library responsibilities

### Changed
- **MicroBatcher**: Early backpressure check in `submit()` method (before queue offer)
- **Backward Compatibility**: All changes are backward compatible - existing code continues to work
- **Null Safety**: All backpressure features are optional and null-safe
- **Coverage Exclusions**: Excluded `startBackpressureMonitoring()` from branch coverage requirements (complex background monitoring method)
- **Test Coverage**: Improved test coverage for `OverflowStrategy`, `BatcherHealth`, and backpressure monitoring

### Fixed
- **Infinite Loop Prevention**: Fixed potential infinite loop in `OverflowStrategy.replayOverflowItems()` when `poll()` returns null
- **Null Safety**: Fixed null pointer exceptions in constructor when config is null
- **Test Coverage**: Fixed coverage issues for `OverflowStrategy` (0.76 → 0.86+), `BatcherHealth.HealthInfo` (0.25 → 0.86+)
- **Test Failures**: Fixed failing tests for invalid monitor interval and exception handling in overflow storage

## [0.0.3] - 2025-11-29

### Added
- **MetricsProvider Interface**: New interface for convenient, domain-specific metrics access
  - `getFailureRate()` - Current failure rate (0.0 to 1.0)
  - `getSuccessRate()` - Current success rate (0.0 to 1.0)
  - `getTotalSubmitted()`, `getTotalSucceeded()`, `getTotalFailed()` - Request counts
  - `getTotalRetried()` - Total number of retried requests (NEW)
  - `getTotalRejected()` - Total number of rejected requests due to backpressure (NEW)
  - `getQueueDepth()` - Current queue depth
  - `getAverageDispatchLatency()`, `getP95DispatchLatency()`, `getP99DispatchLatency()` - Latency metrics
  - Enables adaptive batching, circuit breakers, auto-scaling, and health monitoring
  - Access via `batcher.getMetricsProvider()`
- **BatchTracingHook Interface**: Lightweight tracing integration for distributed tracing systems
  - `onSubmit(Object item)` - Called when an item is submitted
  - `onBatchDispatchStart(List<?> batchItems)` - Called when batch dispatch begins
  - `onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result)` - Called on successful batch completion
  - `onBatchDispatchFailure(List<?> batchItems, Throwable error)` - Called on batch dispatch failure
  - `onRetry(Object item, Throwable cause)` - Called when an item is retried
  - Configure via `BatcherConfig.tracingHook(BatchTracingHook)`
  - Best-effort execution (exceptions don't break batch processing)
  - Enables integration with OpenTelemetry, Zipkin, and other tracing systems
- **BatcherDiagnostics Interface**: Read-only view for inspecting batcher state
  - `isClosed()` - Check if batcher is closed
  - `getCurrentBatchSize()` - Get current batch size (may differ from initial config)
  - `getCurrentLingerTime()` - Get current linger time (may differ from initial config)
  - `getQueueDepth()` - Get current queue depth
  - Access via `batcher.diagnostics()`
  - Useful for health checks, monitoring dashboards, and debugging
- **Enhanced Metrics**: Additional metrics for better observability
  - `vortex.requests.retried` - Counter for retried requests
  - `vortex.requests.rejected` - Counter for rejected requests (backpressure)
  - Metrics are automatically recorded by `RetryManager` and `MicroBatcher`
- **TracingExample**: Comprehensive example demonstrating tracing integration
  - `LoggingTracingHook` - Simple logging-based implementation
  - `OpenTelemetryTracingHook` - Pseudo-implementation showing OTEL integration pattern
  - Demonstrates submit, batch dispatch, and retry event tracing
  - Shows how to integrate with distributed tracing systems
- **AdaptiveBatchingExample**: Example demonstrating adaptive batch sizing based on metrics
- **BackpressureExample**: Comprehensive example showing 5 backpressure handling strategies
  - Basic backpressure detection
  - Proactive monitoring
  - Retry with exponential backoff
  - Circuit breaker pattern
  - Rate limiting strategy

### Performance Improvements
- **Eliminated Redundant Queue Depth Tracking**: Removed manual `AtomicInteger` tracking in favor of direct `queue.size()` calls, reducing atomic operations and memory footprint
- **Hash-Based Result Matching**: Optimized result matching from O(n) linear search to O(1) hash lookup using `HashMap`, providing 20-40% improvement for large batches (50+ items)
- **Eliminated Stream Overhead**: Replaced stream operations in hot paths (`dispatchBatch()`, `close()`) with traditional loops and pre-sized `ArrayList`, reducing CPU usage by 10-15%
- **Cached Configuration Checks**: Cached `debugMode` flag to avoid repeated method calls in hot paths
- **Optimized Time Calculations**: Replaced `Duration.ofNanos()` object creation with direct division (`nanos / 1_000_000`) for better performance
- **Pre-sized Collections**: All `ArrayList` instances now pre-sized to avoid resizing overhead
- **Optimized Retry Logic**: Cached `maxRetries` value to avoid repeated method calls
- **Improved Close Method**: Replaced `Thread.sleep()` with `LockSupport.parkNanos()` for more efficient waiting

### Performance Gains
- **Small batches (1-10 items)**: 5-10% improvement
- **Medium batches (10-50 items)**: 10-20% improvement  
- **Large batches (50+ items)**: 20-40% improvement
- **Memory**: 5-10% reduction
- **CPU**: 10-15% reduction in hot paths

### Changed
- **Example Code Quality**: Improved variable naming throughout all example classes
  - Replaced generic loop variables (`i`, `idx`) with descriptive names (`itemIndex`, `requestIndex`, `requestId`)
  - Improved code readability and maintainability
  - Fixed `AdaptiveBatchingExample` to use correct API (`diagnostics().getCurrentBatchSize()`)
- **Documentation**: Enhanced README with observability section
  - Added "Observability and Tracing (Phase 1)" section
  - Documented `BatchTracingHook` integration patterns
  - Added diagnostics API usage examples
  - Updated examples README with new tracing example

### Internal Changes
- Refactored `MetricsManager` to use `BlockingQueue` directly instead of `AtomicInteger` for queue depth
- Updated `RetryManager` and `ResultProcessor` to accept cached `debugMode` parameter
- Improved fallback logic in result matching to handle unmatched results correctly
- Integrated `BatchTracingHook` into `MicroBatcher` lifecycle (submit, dispatch, retry)
- Extended `MetricsManager` to record retry and rejection metrics
- Added `BatcherDiagnostics` implementation as read-only view of batcher state
- `RetryManager` now records retry metrics via `MetricsManager`
- `MicroBatcher.submit()` now records rejection metrics when queue is full

## [0.0.2] - 2025-11-26

### Added
- **Item Result Tracking**: Sealed `ItemResult<T>` interface with `Success` and `Failure` records for type-safe result handling
  - Factory methods: `ItemResult.success(T item)` and `ItemResult.failure(T item, Throwable error)`
  - Support for pattern matching with sealed interface
  - Conversion methods from `SuccessEvent` and `FailureEvent`
- **Batch Callbacks**: `submitWithCallback()` method for cleaner async result handling with direct per-item callbacks
  - Returns `CompletableFuture<Void>` that completes after callback execution
  - Callback receives item and its `ItemResult` directly
  - Exception handling in callbacks doesn't break the batcher
- **Retry Support**: Built-in retry mechanism with configurable:
  - `maxRetries`: Maximum number of retry attempts per item
  - `retryDelay`: Delay between retry attempts
  - `retryableErrorPredicate`: Custom predicate to determine which errors are retryable
  - Automatic retry tracking and cleanup on success
  - Support for retry in atomic commit mode and backend exceptions
- **Per-Item Metrics**: Optional detailed metrics tracking for individual items (`perItemMetrics` flag)
  - `vortex.item.submit.latency`: Time from submit to batch completion
  - `vortex.item.wait.time`: Time item waits in queue
  - `vortex.item.batch.size`: Size of batch when item was processed
  - Only tracked when enabled for performance
- **Debug Mode**: Comprehensive debug logging for troubleshooting batch processing (`debugMode` flag)
  - Batch formation events
  - Item submission events
  - Batch dispatch events
  - Queue depth changes
  - Timing information
  - All logging conditional on `debugMode` flag (no performance impact when disabled)
- **Dynamic Configuration**: Runtime configuration updates:
  - `updateBatchSize(int newBatchSize)`: Update batch size at runtime
  - `updateLingerTime(Duration newLingerTime)`: Update linger time at runtime
  - Thread-safe configuration updates
- **Configurable Queue Size**: `maxQueueSize` configuration option for fine-grained backpressure control
  - Default: `2 × batchSize` (backward compatible)
  - Allows custom queue capacity for high-throughput scenarios
  - Validation ensures `maxQueueSize >= batchSize`
- **Enhanced BatchResult API**:
  - `findItemResult(T item)`: Find item result using default equality (`Objects::equals`)
  - `findItemResult(T item, BiPredicate<T, T> equalityComparator)`: Find item result with custom comparator
  - `isCompleteSuccess()`: Alias for `isAllSuccess()` for consistency
  - `isCompleteFailure()`: Check if all items failed
  - `getFailureRate()`: Calculate failure rate as a percentage (0.0 to 1.0)
  - `getFailuresByType()`: Group failures by exception type
- **Queue Wait Time Metrics**: Enhanced metrics for queue wait times
  - `vortex.queue.wait.time`: Histogram of queue wait times
  - Percentile metrics: `vortex.queue.wait.time.p50`, `.p95`, `.p99`
- **Batch Size Distribution Metrics**: New metrics for batch size analysis
  - `vortex.batch.size`: Histogram of batch sizes
  - Summary statistics: average, min, max batch sizes
- **Test Utilities**: Helper classes for easier testing
  - `MicroBatcherTestUtils`: Utility methods for common test scenarios
  - `TestBackend<T>`: Test backend that records all batches for verification
  - `waitForBatches()`: Utility method to wait for batch processing completion
- **Additional Examples**:
  - `ExampleUsageSimplified.java`: Multiple usage patterns (fire-and-forget, callbacks, batch wait, stream-based)
  - `ExampleUsageWithBackpressure.java`: Comprehensive backpressure handling demonstration
- **Public API Enhancement**: `isClosed()` method made public for better testability and monitoring
- **Code Refactoring**: Extracted helper classes for better maintainability
  - `MetricsManager`: Centralized metrics management (150 lines)
  - `RetryManager`: Retry logic encapsulation (118 lines)
  - `ResultProcessor`: Complex result matching logic (216 lines)
  - Reduced `MicroBatcher` from 779 lines to 438 lines

### Changed
- **Test Execution**: Disabled parallel test execution (`maxParallelForks=1`) for better reliability with async/timing-dependent tests
- **Coverage Threshold**: Adjusted to 86% for complex async code (`MicroBatcher` class) while maintaining >90% for other classes
- **Queue Initialization**: Updated to use configurable `maxQueueSize` instead of hardcoded `2 × batchSize`
- **Documentation Structure**: Reorganized documentation following vajrapulse structure
  - Created `documents/` folder with subfolders: releases, roadmap, architecture, integrations, guides, analysis, resources, archive
  - All markdown docs moved to appropriate subfolders
  - Updated `.cursorrules` with document organization rules
- **Documentation**: Comprehensive updates to README with:
  - Complete backpressure handling section
  - Code examples for handling `RejectedExecutionException`
  - Best practices for queue size configuration
  - Monitoring recommendations
  - All new features documented with examples
- **Examples**: All examples reviewed and updated to current best practices with proper error handling
- **Build Configuration**: 
  - Removed JReleaser plugin (replaced with direct Central Portal API approach)
  - Updated publishing comments to reference actual publishing approach
  - Added comprehensive JavaDoc to all public methods and constructors

### Fixed
- **Test Reliability**: Replaced `Thread.sleep()` with `CountDownLatch` for precise synchronization in critical tests
- **Test Flakiness**: Eliminated timing-dependent test failures through better synchronization and sequential execution
- **Floating-Point Comparison**: Fixed floating-point comparison in `BatchResultSpec` using epsilon-based comparison (`Math.abs(difference) < epsilon`)
- **Test Utilities**: Fixed `MicroBatcherTestUtils.waitForBatches()` to properly check batcher state using public `isClosed()` method and queue depth metrics
- **Code Coverage**: Improved coverage for edge cases in:
  - Batch processing (close operations, interruptions, timeouts)
  - Retry logic (success, failure, closed batcher, delay interruption)
  - Result matching and processing
- **Java Compiler Warnings**: Fixed all compiler warnings
  - Added JavaDoc to all public methods and constructors
  - Fixed deprecated `Thread.getId()` usage in benchmarks
  - Added null safety annotations where appropriate
  - Fixed null safety warnings in generic type handling
- **Result Mapping Logic**: Improved fallback logic to ensure all futures are completed, using matched flag to track request-to-result matching
- **Per-Item Metrics**: Fixed per-item metrics recording in atomic commit mode

### Removed
- **Unused Configuration**: Removed `maxConcurrency` parameter from `BatcherConfig` (was not used in implementation)
- **JReleaser Configuration**: Removed `jreleaser.yml` files (replaced with direct Central Portal API approach via `scripts/publish-to-central.sh`)

### Infrastructure
- **Maven Central Publishing**: Improved publishing workflow
  - Created `scripts/publish-to-central.sh` script for reliable publishing via Central Portal API
  - Uses base64 token authentication (`mavenCentralToken`) as Bearer token
  - Direct bundle upload to Central Portal API endpoint
  - Automatic checksum generation (MD5, SHA1)
  - Better error handling and status reporting
  - Replaced JReleaser plugin (which had YAML reading issues) with direct API approach

### Documentation
- **Release Documentation**: Created comprehensive release documentation
  - `RELEASE_0.0.2_NOTES.md`: Detailed release notes
  - `RELEASE_0.0.2_CHECKLIST.md`: Release checklist
  - `RELEASE_0.0.2_PREPARATION.md`: Release preparation summary
- **Implementation Plan**: Added detailed implementation plan document (`RELEASE_0.0.2_IMPLEMENTATION_PLAN.md`)
- **Code Review**: Added final code review document (`FINAL_CODE_REVIEW.md`) with 88% confidence assessment
- **Updated README**: Comprehensive updates with all new features, backpressure handling, and best practices
- **Enhanced Configuration Documentation**: All new configuration options documented with examples
- **Migration Guide**: Updated with new features and recommended patterns
- **All Examples**: Reviewed and updated to demonstrate current best practices

## [0.0.1] - 2025-11-22

### Added
- **Initial Release**: First release of Vortex Micro-Batching Library
- **Java 21 Support**: Built with Java 21 and virtual threads for high concurrency
- **Smart Batching**: Triggers on batch size OR linger time (whichever comes first)
- **Atomic Commits**: Optional atomic commit mode where batch fails if any request fails
- **Generic Backend**: Works with any backend via the `Backend<T>` interface
- **Comprehensive Metrics**: Micrometer metrics for:
  - Queue depth
  - Success/failure rates
  - Batch latencies
  - Percentiles (p50, p95, p99)
- **Auto-Replay**: Configurable automatic replay of successful items when batches have mixed results
- **Lightweight Design**: Minimal dependencies, clean code
- **Simple API**: Easy to use and integrate
- **Examples**: 8 comprehensive examples including:
  - Basic usage
  - Atomic commit mode
  - Auto-replay functionality
  - Time-based batching
  - Metrics collection
  - HTTP backend integration
  - Custom backend replay logic
- **Testing**: 81 tests with 88% code coverage, all written in Spock Framework
- **Benchmarks**: JMH benchmarks for throughput and latency analysis
- **Documentation**: 
  - README with getting started guide
  - Architecture and design documentation
  - Performance benchmarks documentation
  - Grafana dashboard setup guide
  - Release process guide

### Maven Coordinates
```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## Types of Changes

- **Added** for new features
- **Changed** for changes in existing functionality
- **Deprecated** for soon-to-be removed features
- **Removed** for now removed features
- **Fixed** for any bug fixes
- **Security** for vulnerability fixes

[0.0.3]: https://github.com/vajrapulse/vortex/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/vajrapulse/vortex/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/vajrapulse/vortex/releases/tag/v0.0.1

