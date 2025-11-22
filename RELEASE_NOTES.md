# Release Notes - v0.0.1

## Vortex Micro-Batching Library v0.0.1

**Release Date**: November 22, 2025

### Overview

First release of Vortex, a lightweight Java 21 library for micro-batching requests to any backend with virtual threads support.

### Features

- ✅ **Java 21** with virtual threads for high concurrency
- ✅ **Smart Batching**: Triggers on batch size OR linger time (whichever comes first)
- ✅ **Atomic Commits**: Optional atomic commit mode where batch fails if any request fails
- ✅ **Generic Backend**: Works with any backend via the `Backend<T>` interface
- ✅ **Comprehensive Metrics**: Micrometer metrics for queue depth, success/failure rates, latencies
- ✅ **Auto-Replay**: Configurable automatic replay of successful items when batch has mixed results
- ✅ **Lightweight**: Minimal dependencies, clean code
- ✅ **Simple API**: Easy to use and integrate

### Package Structure

- **Main Package**: `com.vajrapulse.vortex`
- **Examples**: Located in `examples/` directory
- **Benchmarks**: JMH benchmarks in `src/jmh/`

### Maven Coordinates

```xml
<dependency>
    <groupId>com.vajrapulse</groupId>
    <artifactId>vortex</artifactId>
    <version>0.0.1</version>
</dependency>
```

### Gradle Coordinates

```kotlin
implementation("com.vajrapulse:vortex:0.0.1")
```

### Documentation

- [README.md](README.md) - Getting started guide
- [DESIGN.md](DESIGN.md) - Architecture and design decisions
- [BENCHMARKS.md](BENCHMARKS.md) - Performance benchmarks
- [GRAFANA_DASHBOARD.md](GRAFANA_DASHBOARD.md) - Metrics dashboard setup
- [RELEASE.md](RELEASE.md) - Release process guide

### Examples

8 comprehensive examples included:
- Basic usage
- Atomic commit mode
- Auto-replay functionality
- Time-based batching
- Metrics collection
- HTTP backend integration
- Custom backend replay logic

### Testing

- **81 tests** passing
- **88% code coverage**
- All tests written in Spock Framework

### Benchmarks

JMH benchmarks included:
- Throughput benchmarks (ops/sec)
- Latency benchmarks (μs/op)
- HTML reports generated in `build/reports/jmh/html/`

### Breaking Changes

None - This is the first release.

### Known Issues

None at this time.

### Contributors

- VajraPulse Team

### License

Apache License 2.0

