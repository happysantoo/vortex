# Vortex Examples

This directory contains example code demonstrating various features of the Vortex micro-batcher library.

## Running Examples

### Using Gradle

You can compile and run examples using Gradle:

```bash
# Compile examples
javac -cp "$(./gradlew -q printClasspath)" examples/*.java

# Run an example
java -cp "$(./gradlew -q printClasspath):examples" com.vajrapulse.vortex.example.BasicUsageExample
```

### Using IDE

1. Add the Vortex library to your classpath
2. Open any example file
3. Run the `main` method

## Available Examples

- **BasicUsageExample.java** - Simple batching demonstration
- **AtomicCommitExample.java** - Atomic commit mode (all-or-nothing)
- **AutoReplayExample.java** - Automatic replay of successful items
- **TimeBasedBatchingExample.java** - Time-based batching (linger time)
- **MetricsExample.java** - Metrics collection and monitoring
- **HttpBackendExample.java** - HTTP backend integration
- **CustomBackendReplayExample.java** - Custom backend with replay logic
- **ExampleUsage.java** - Comprehensive usage example with submitWithCallback
- **ExampleUsageSimplified.java** - Multiple usage patterns (fire-and-forget, callbacks, etc.)
- **ExampleUsageWithBackpressure.java** - Backpressure handling and queue management

## Prerequisites

- Java 21 or higher
- Vortex library on classpath
- Micrometer (for metrics examples)

