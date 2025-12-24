# Vortex Examples

This directory contains example code demonstrating various features of the Vortex micro-batcher library.

## Running Examples

### Using Gradle

Examples are automatically compiled as part of the build to ensure they stay current with the API:

```bash
# Compile examples (runs automatically during build)
./gradlew compileExamplesJava

# Or compile everything
./gradlew build
```

### Running Examples Manually

You can run examples using the compiled classes:

```bash
# Compile examples first
./gradlew compileExamplesJava

# Run an example
java -cp "build/classes/java/main:build/classes/examples:$(./gradlew -q printClasspath)" \
  com.vajrapulse.vortex.example.BasicUsageExample
```

### Using IDE

1. Add the Vortex library to your classpath
2. Open any example file
3. Run the `main` method

## Available Examples

### Core Submission Methods

- **ThreeSubmissionMethodsExample.java** - Demonstrates all three ways to submit items:
  - `submit(item)` - Synchronous, returns ItemResult immediately
  - `submit(item, callback)` - Synchronous return + async callback for result
  - `submitAsync(item)` - Returns CompletableFuture for async processing

### Error Handling

- **ErrorHandlingExample.java** - Comprehensive error handling:
  - Queue full handling (ItemRejectedException)
  - Backend error processing
  - Retry configuration and behavior

### Basic Usage

- **BasicUsageExample.java** - Simple getting started example
- **ExampleUsage.java** - Comprehensive usage patterns

### Advanced Features

- **AtomicCommitExample.java** - Atomic commit mode (all-or-nothing)
- **AutoReplayExample.java** - Automatic replay of successful items
- **TimeBasedBatchingExample.java** - Time-based batching (linger time)
- **CustomBackendReplayExample.java** - Custom backend with replay logic

### Observability

- **MetricsExample.java** - Metrics collection and monitoring
- **TracingExample.java** - BatchTracingHook integration for observability
  - Logging-based tracing hook
  - OpenTelemetry-style integration pattern
  - Retry event tracing


## Example Comparison Table

| Example | Demonstrates | Submission Method |
|---------|-------------|-------------------|
| **ThreeSubmissionMethodsExample** | All three submission methods | All three |
| **ErrorHandlingExample** | Error handling patterns | All three |
| **BasicUsageExample** | Simple usage | `submit(item)` |
| **ExampleUsage** | Comprehensive patterns | `submit(item, callback)` |
| **AtomicCommitExample** | Atomic commit mode | `submit(item, callback)` |
| **AutoReplayExample** | Auto-replay feature | `submit(item, callback)` |
| **TimeBasedBatchingExample** | Time-based batching | `submit(item)` |
| **MetricsExample** | Metrics usage | `submitAsync(item)` |
| **TracingExample** | Tracing integration | `submit(item, callback)` |
| **CustomBackendReplayExample** | Custom backend | `submit(item, callback)` |

## Prerequisites

- Java 21 or higher
- Vortex library on classpath (automatically included when using Gradle)
- Micrometer (for metrics examples)
- SLF4J (for logging examples)

## Example Maintenance

Examples are automatically compiled as part of the build process to ensure they stay current with the API. If an example fails to compile, it indicates that the API has changed and the example needs to be updated.

See `.cursorrules` for maintenance requirements when the API changes.
