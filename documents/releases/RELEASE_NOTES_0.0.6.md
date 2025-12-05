# Release 0.0.6

**Release Date:** 2025-12-05

## Overview

Release 0.0.6 focuses on comprehensive documentation enhancements, improved developer experience, and better integration guidance for the Vortex Micro-Batching Library.

---

## Documentation Enhancements

### Enhanced JavaDoc

- **`submitSync()` Method**: Added comprehensive JavaDoc with:
  - Clear explanation of SUCCESS vs REJECTED results
  - Important notes about not waiting for batch processing
  - Detailed example usage including integration with load testing frameworks
  - Performance characteristics and use cases

- **`submitWithCallback()` Method**: Enhanced JavaDoc with:
  - Callback timing details (immediate rejection vs. after batch processing)
  - Thread safety notes
  - Distinction from `submitSync()` method
  - Example usage including load testing framework integration

- **`getQueueDepth()` Method**: Enhanced JavaDoc with:
  - Use cases (monitoring, `QueueDepthBackpressureProvider`)
  - Note about snapshot behavior
  - Example usage

- **Factory Methods**: Enhanced JavaDoc for all factory methods:
  - `forHighThroughput()`: Performance characteristics (throughput, latency, memory)
  - `forLowLatency()`: Performance characteristics and use cases
  - `forBalanced()`: Balanced performance profile
  - `forResilient()`: Resilience-focused configuration
  - Clarified when to use each factory method

- **`QueueDepthBackpressureProvider`**: Enhanced JavaDoc with:
  - Clear explanation of queue-only backpressure approach
  - Integration examples
  - Configuration recommendations

### New Documentation

- **Adaptive Load Testing Guide** (`documents/guides/ADAPTIVE_LOAD_TESTING_GUIDE.md`):
  - Comprehensive guide for queue-only backpressure approach
  - Integration examples with VajraPulse AdaptiveLoadPattern
  - Configuration recommendations
  - Best practices
  - Troubleshooting guide

---

## Improvements

### Developer Experience

- Better documentation for synchronous submission API
- Clearer guidance on when to use different submission methods
- Enhanced factory method documentation for easier selection
- Comprehensive usage guide for adaptive load testing scenarios

### Code Quality

- All documentation enhancements maintain code simplicity
- Test coverage remains >90%
- No breaking changes

---

## Migration Guide

No migration required. This is a documentation-only release with no API changes.

---

## Full Changelog

See [CHANGELOG.md](../../CHANGELOG.md) for complete details.

---

## Links

- **GitHub Release**: https://github.com/happysantoo/vortex/releases/tag/v0.0.6
- **Maven Central**: https://central.sonatype.com/artifact/com.vajrapulse/vortex/0.0.6
- **Documentation**: See `documents/guides/ADAPTIVE_LOAD_TESTING_GUIDE.md`

---

**Note:** This release focuses on documentation improvements to help developers better understand and use the Vortex library, particularly for adaptive load testing scenarios.

