# Release Notes - Vortex 0.0.7

**Release Date**: December 6, 2025

## Overview

Version 0.0.7 includes significant improvements from a principal engineer code review, focusing on connection pool exhaustion prevention, memory leak fixes, API simplification, and enhanced error handling.

## 🎉 New Features

### Concurrent Batch Dispatch Limiter
- New `maxConcurrentBatches` configuration to prevent connection pool exhaustion
- Limits the number of batches that can be dispatched concurrently
- Recommended value: 80% of connection pool size
- Prevents overwhelming connection pools by controlling concurrent batch dispatches
- Rejected batches are handled gracefully with proper error notifications

### Enhanced Metrics
- `vortex.dispatch.rejected` - Counter for batches rejected due to concurrent dispatch limit
- `vortex.dispatch.active.batches` - Gauge for current number of batches being dispatched concurrently

### Graceful Shutdown
- New `awaitCompletion()` method for waiting on queue and in-flight batches
- Waits for all queued items to be processed
- Waits for all in-flight batches to complete
- Useful for test teardown and application shutdown scenarios
- Handles interruption and timeouts gracefully

### CompositeBackpressureProvider Builder
- Builder pattern for easier composite provider construction
- Fluent API for combining multiple backpressure providers
- Convenience method `queueDepth()` for adding queue depth provider
- `add()` method for adding custom providers
- Cleaner, more intuitive API compared to constructor-based approach

### RetryManager Memory Leak Prevention
- Automatic cleanup of retry count entries
- Size limit (10,000 entries) to prevent unbounded growth
- Periodic cleanup (every 5 minutes) of stale entries
- Automatic eviction when limit is reached
- Prevents memory leaks in high-retry scenarios

## 🔄 Changes

### Shutdown Behavior
- Enhanced `close()` method to wait for in-flight batches when concurrent limiting is enabled
- Prevents race conditions during shutdown
- Ensures all batches complete before executor shutdown

### BatcherConfig
- Added `maxConcurrentBatches` configuration option
- Default: 0 (unlimited)
- Must be >= 0 when set (0 means unlimited)
- Integrated into builder pattern

### API Simplification
- Removed `MicroBatcher.withBackpressure()` factory methods
- All backpressure configuration now via `BatcherConfig.builder()`
- Cleaner, more consistent API
- Backpressure provider and strategy configured directly in config

### Error Messages
- Enhanced error messages with context
- `IllegalStateException` messages now include queue depth and active batch count
- Better debugging information when batcher is closed
- Applied to all submission and configuration methods

## 🐛 Fixes

### Race Condition
- Fixed TOCTOU race condition in `submitSync()` queue check
- Removed redundant queue size check
- Now relies directly on atomic `queue.offer()` operation
- Eliminates race condition window

### Memory Leak
- Fixed potential memory leak in `RetryManager`
- Added size limit and periodic cleanup
- Prevents unbounded growth of retry count map

### Code Clarity
- Improved `activeBatchCount` increment timing
- Now incremented after successful `executor.submit()` call
- Clearer semantics and simpler error handling

## 📚 Documentation

- README updates with examples for concurrent dispatch limiting and graceful shutdown
- Enhanced JavaDoc for new features
- Principal engineer code review document added
- Comprehensive code review for correctness and improvements
- All identified issues addressed

## 🔗 Links

- [Full Changelog](https://github.com/happysantoo/vortex/blob/main/CHANGELOG.md)
- [Principal Engineer Review](documents/analysis/PRINCIPAL_ENGINEER_REVIEW_0.0.7.md)

## 📦 Maven Coordinates

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.7</version>
</dependency>
```

## 🙏 Acknowledgments

Thanks to the principal engineer review that identified key improvements and helped make this release more robust and production-ready.

