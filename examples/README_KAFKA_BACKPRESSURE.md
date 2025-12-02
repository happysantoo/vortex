# Kafka Consumer Backpressure Example

This example demonstrates how to use the Vortex Micro-Batching Library with a Kafka consumer and backpressure handling using `OverflowStrategy`.

## Overview

The example shows a complete integration of:
- **Kafka Consumer** (Application responsibility)
- **Vortex Micro-Batching Library** (Library responsibility)
- **Backpressure Handling** (Library responsibility)
- **Overflow Management** (Library responsibility)

## Responsibilities

### Application Responsibilities

The application is responsible for:

1. **Kafka Consumer Setup**
   - Creating and configuring the Kafka consumer
   - Subscribing to topics
   - Polling for records
   - Committing offsets

2. **Business Logic**
   - Processing individual items (e.g., database writes, API calls)
   - Error handling for business logic failures
   - Logging application-specific events

3. **Consumer Lifecycle Management**
   - Implementing `pauseKafkaConsumer()` - pauses partitions when called
   - Implementing `resumeKafkaConsumer()` - resumes partitions when called
   - These methods are called by the library at appropriate times

4. **Application Lifecycle**
   - Starting/stopping the consumer
   - Graceful shutdown
   - Monitoring application metrics

### Library Responsibilities

The Vortex library is responsible for:

1. **Batching**
   - Collecting items into batches
   - Dispatching batches to the backend
   - Managing batch size and timing

2. **Backpressure Detection**
   - Monitoring queue depth
   - Calculating backpressure level (0.0-1.0)
   - Detecting when threshold is exceeded

3. **Overflow Management**
   - Storing items to overflow storage when backpressure is high
   - Monitoring backpressure state transitions
   - Replaying items from overflow when capacity becomes available

4. **Lifecycle Callbacks**
   - Calling `onPause` callback when backpressure enters high state
   - Calling `onResume` callback when backpressure resolves
   - Calling `onBackpressureActive` periodically while active

5. **Metrics and Diagnostics**
   - Providing queue depth, batch size, linger time
   - Tracking submitted, succeeded, failed, rejected, dropped counts
   - Measuring latencies

## How It Works

### Normal Operation

1. Kafka consumer polls for records
2. Application extracts values and submits to `MicroBatcher`
3. Library batches items and dispatches to backend
4. Backend processes batch (application business logic)
5. Results are returned to callers

### Backpressure Scenario

1. **Queue fills up** (e.g., backend is slow)
2. **Library detects backpressure** (queue depth > threshold)
3. **Library stores items to overflow** instead of queue
4. **Library calls `onPause` callback** → Application pauses Kafka consumer
5. **Library monitors backpressure** (100ms interval)
6. **Backend catches up** → Queue depth decreases
7. **Library detects resolution** (queue depth < threshold)
8. **Library replays items from overflow** back to queue
9. **Library calls `onResume` callback** → Application resumes Kafka consumer
10. **Normal operation resumes**

## Key Components

### QueueDepthBackpressureProvider

```java
BackpressureProvider provider = new QueueDepthBackpressureProvider(
    () -> batcher.diagnostics().getQueueDepth(), // Queue depth supplier
    MAX_QUEUE_SIZE // Maximum capacity
);
```

**Library Responsibility**: Calculates backpressure level based on queue depth using linear scaling:
- `queueDepth = 0` → `backpressure = 0.0`
- `queueDepth < maxCapacity` → `backpressure = queueDepth / maxCapacity`
- `queueDepth >= maxCapacity` → `backpressure = 1.0`

### OverflowStrategy

```java
OverflowStrategy<String> strategy = new OverflowStrategy<>(
    0.7, // Threshold (70% capacity)
    overflowStorage, // Storage for overflowed items
    backpressureProvider, // Provider for monitoring
    item -> batcher.submit(item), // Resubmit function
    () -> kafkaConsumer.pause(), // Pause callback
    () -> kafkaConsumer.resume() // Resume callback
);
```

**Library Responsibility**:
- When backpressure >= threshold: stores items to overflow, calls pause callback
- While active: monitors for resolution, may start gradual replay
- When backpressure < threshold: replays items, calls resume callback

### InMemoryOverflowStorage

```java
OverflowStorage<String> overflow = new InMemoryOverflowStorage<>();
```

**Library Responsibility**: Provides temporary in-memory storage for overflowed items.

## Running the Example

### Prerequisites

1. **Kafka** running on `localhost:9092`
2. **Topic** named `events` created
3. **Dependencies** (if running standalone):
   ```xml
   <dependency>
       <groupId>org.apache.kafka</groupId>
       <artifactId>kafka-clients</artifactId>
       <version>3.6.0</version>
   </dependency>
   ```

### Running

```bash
# Compile
javac -cp ".:vortex-0.0.4.jar:kafka-clients-3.6.0.jar:..." \
    examples/KafkaConsumerBackpressureExample.java

# Run
java -cp ".:vortex-0.0.4.jar:kafka-clients-3.6.0.jar:..." \
    com.vajrapulse.vortex.example.KafkaConsumerBackpressureExample
```

### Spring Boot Integration

In a Spring Boot application, you would typically:

```java
@Component
public class KafkaEventConsumer {
    
    @Autowired
    private KafkaConsumer<String, String> kafkaConsumer;
    
    private MicroBatcher<String> batcher;
    
    @PostConstruct
    public void init() {
        // Create batcher with backpressure support
        batcher = createMicroBatcher();
    }
    
    @KafkaListener(topics = "events")
    public void consume(ConsumerRecord<String, String> record) {
        // Submit to batcher - library handles batching and backpressure
        batcher.submit(record.value());
    }
    
    // ... rest of implementation
}
```

## Monitoring

The example includes a metrics printing thread that shows:

- **Queue Depth**: Current items in queue
- **Batch Size**: Current batch size setting
- **Linger Time**: Current linger time setting
- **Total Submitted**: Total items submitted
- **Total Succeeded**: Successfully processed items
- **Total Failed**: Failed items
- **Total Backpressure Rejected**: Items rejected due to backpressure
- **Total Backpressure Dropped**: Items dropped due to backpressure
- **Average Batch Dispatch Latency**: Average time to dispatch batch

## Benefits

1. **Automatic Backpressure Handling**: Library manages overflow and replay
2. **Consumer Pause/Resume**: Library coordinates with Kafka consumer lifecycle
3. **No Data Loss**: Items are stored in overflow, not dropped
4. **Transparent**: Application code remains simple
5. **Observable**: Comprehensive metrics and diagnostics

## Customization

### Custom Backpressure Provider

```java
// Monitor external resource (e.g., database connection pool)
BackpressureProvider customProvider = new BackpressureProvider() {
    @Override
    public double getBackpressureLevel() {
        // Calculate based on your resource
        return connectionPool.getActiveConnections() / connectionPool.getMaxConnections();
    }
    
    @Override
    public String getSourceName() {
        return "Database Connection Pool";
    }
};
```

### Custom Overflow Storage

```java
// Use disk-based storage for larger overflow scenarios
OverflowStorage<String> diskStorage = new DiskOverflowStorage<>("/tmp/overflow");
```

### Composite Backpressure

```java
// Combine multiple backpressure sources
BackpressureProvider composite = new CompositeBackpressureProvider(
    queueProvider,
    connectionPoolProvider,
    customProvider
);
```

## Troubleshooting

### Consumer Not Pausing

- Check that `onPause` callback is correctly implemented
- Verify backpressure threshold is appropriate
- Check queue depth is actually exceeding threshold

### Items Not Replaying

- Verify `onResume` callback is correctly implemented
- Check overflow storage is not empty
- Ensure backpressure is actually resolving (queue depth decreasing)

### High Memory Usage

- Consider using bounded `InMemoryOverflowStorage`
- Implement disk-based overflow storage for large scenarios
- Adjust backpressure threshold to trigger earlier

## See Also

- `BackpressureExample.java` - Basic backpressure examples
- `VORTEX_BACKPRESSURE_ENHANCEMENTS.md` - Design documentation
- `RELEASE_NOTES_0.0.4.md` - Release notes

