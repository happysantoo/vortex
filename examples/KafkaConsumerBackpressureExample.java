package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.backpressure.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example demonstrating Vortex Micro-Batching Library with Spring Kafka Consumer
 * and backpressure handling using OverflowStrategy.
 * 
 * <p>This example shows:
 * <ul>
 *   <li>How to integrate Vortex with Kafka consumer</li>
 *   <li>How to use OverflowStrategy for backpressure handling</li>
 *   <li>How to pause/resume Kafka consumer based on backpressure</li>
 *   <li>Clear separation of application vs library responsibilities</li>
 * </ul>
 * 
 * <p><b>Application Responsibilities:</b>
 * <ul>
 *   <li>Kafka consumer setup and configuration</li>
 *   <li>Polling Kafka for records</li>
 *   <li>Pausing/resuming consumer partitions</li>
 *   <li>Business logic (processing items)</li>
 *   <li>Error handling and logging</li>
 * </ul>
 * 
 * <p><b>Library Responsibilities:</b>
 * <ul>
 *   <li>Batching items efficiently</li>
 *   <li>Detecting backpressure (queue depth)</li>
 *   <li>Storing items to overflow when backpressure is high</li>
 *   <li>Monitoring backpressure state transitions</li>
 *   <li>Replaying items from overflow when capacity available</li>
 *   <li>Calling pause/resume callbacks at appropriate times</li>
 *   <li>Providing metrics and diagnostics</li>
 * </ul>
 */
public class KafkaConsumerBackpressureExample {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerBackpressureExample.class);
    
    // Application configuration
    private static final String KAFKA_TOPIC = "events";
    private static final int BATCH_SIZE = 50;
    private static final Duration LINGER_TIME = Duration.ofMillis(100);
    private static final int MAX_QUEUE_SIZE = 1000;
    private static final double BACKPRESSURE_THRESHOLD = 0.7; // 70% queue capacity
    
    // Application state
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final MicroBatcher<String> batcher;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong overflowCount = new AtomicLong(0);
    
    public KafkaConsumerBackpressureExample(KafkaConsumer<String, String> kafkaConsumer) {
        this.kafkaConsumer = kafkaConsumer;
        this.batcher = createMicroBatcher();
    }
    
    /**
     * Creates and configures the MicroBatcher with backpressure support.
     * 
     * <p><b>Library Responsibility:</b> This method demonstrates how the library
     * handles backpressure configuration and setup.
     */
    private MicroBatcher<String> createMicroBatcher() {
        // Backend: Application's business logic
        // APPLICATION RESPONSIBILITY: Define what to do with batched items
        Backend<String> backend = batch -> {
            logger.info("Processing batch of {} items", batch.size());
            
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                try {
                    // APPLICATION RESPONSIBILITY: Business logic for processing each item
                    processItem(item);
                    successes.add(new SuccessEvent<>(item));
                    processedCount.incrementAndGet();
                } catch (Exception e) {
                    logger.error("Failed to process item: {}", item, e);
                    failures.add(new FailureEvent<>(item, e));
                }
            }
            
            return new BatchResult<>(successes, failures);
        };
        
        // Configuration
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(BATCH_SIZE)
            .lingerTime(LINGER_TIME)
            .maxQueueSize(MAX_QUEUE_SIZE)
            .build();
        
        // Backpressure Provider: Library monitors queue depth
        // LIBRARY RESPONSIBILITY: Detects backpressure based on queue depth
        BackpressureProvider backpressureProvider = new QueueDepthBackpressureProvider(
            () -> batcher != null ? batcher.diagnostics().getQueueDepth() : 0,
            MAX_QUEUE_SIZE
        );
        
        // Overflow Storage: Library manages temporary storage
        // LIBRARY RESPONSIBILITY: Provides overflow storage for items during backpressure
        OverflowStorage<String> overflowStorage = new InMemoryOverflowStorage<>();
        
        // Overflow Strategy: Library handles overflow and lifecycle
        // LIBRARY RESPONSIBILITY: 
        // - Stores items to overflow when backpressure is high
        // - Monitors backpressure state transitions
        // - Replays items when capacity becomes available
        // - Calls pause/resume callbacks at appropriate times
        OverflowStrategy<String> overflowStrategy = new OverflowStrategy<>(
            BACKPRESSURE_THRESHOLD,
            overflowStorage,
            backpressureProvider,
            item -> {
                // Resubmit item to batcher when replaying from overflow
                overflowCount.decrementAndGet();
                return batcher.submit(item);
            },
            // APPLICATION RESPONSIBILITY: Pause Kafka consumer
            // Called by library when backpressure enters high state
            () -> {
                logger.warn("Backpressure detected - pausing Kafka consumer");
                pauseKafkaConsumer();
            },
            // APPLICATION RESPONSIBILITY: Resume Kafka consumer
            // Called by library when backpressure resolves
            () -> {
                logger.info("Backpressure resolved - resuming Kafka consumer");
                resumeKafkaConsumer();
            }
        );
        
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Create batcher with backpressure support
        // LIBRARY RESPONSIBILITY: Manages batching, backpressure detection, and overflow
        return MicroBatcher.withBackpressure(
            backend,
            config,
            meterRegistry,
            backpressureProvider,
            overflowStrategy
        );
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Business logic for processing a single item.
     * This is where your application-specific processing happens.
     */
    private void processItem(String item) throws Exception {
        // Simulate processing (e.g., database write, API call, etc.)
        // In a real application, this would be your business logic
        Thread.sleep(10); // Simulate processing time
        
        // Example: Parse JSON, validate, transform, etc.
        logger.debug("Processed item: {}", item);
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Pause Kafka consumer partitions.
     * Called by the library when backpressure is detected.
     */
    private void pauseKafkaConsumer() {
        try {
            Set<TopicPartition> partitions = kafkaConsumer.assignment();
            if (!partitions.isEmpty()) {
                kafkaConsumer.pause(partitions);
                logger.info("Paused {} Kafka partitions", partitions.size());
            }
        } catch (Exception e) {
            logger.error("Error pausing Kafka consumer", e);
        }
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Resume Kafka consumer partitions.
     * Called by the library when backpressure resolves.
     */
    private void resumeKafkaConsumer() {
        try {
            Set<TopicPartition> partitions = kafkaConsumer.assignment();
            if (!partitions.isEmpty()) {
                kafkaConsumer.resume(partitions);
                logger.info("Resumed {} Kafka partitions", partitions.size());
            }
        } catch (Exception e) {
            logger.error("Error resuming Kafka consumer", e);
        }
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Main consumer loop.
     * Polls Kafka and submits items to the batcher.
     */
    public void run() {
        logger.info("Starting Kafka consumer with backpressure support");
        
        try {
            while (running.get()) {
                // APPLICATION RESPONSIBILITY: Poll Kafka for records
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                
                if (records.isEmpty()) {
                    continue;
                }
                
                logger.debug("Polled {} records from Kafka", records.count());
                
                // APPLICATION RESPONSIBILITY: Extract and submit items to batcher
                // LIBRARY RESPONSIBILITY: Handles batching, backpressure, and overflow
                for (ConsumerRecord<String, String> record : records) {
                    String value = record.value();
                    
                    // Submit to batcher
                    // If backpressure is high, library will:
                    // 1. Store item to overflow storage
                    // 2. Call pause callback (pauses Kafka consumer)
                    // 3. Monitor for resolution
                    // 4. Replay items when capacity available
                    // 5. Call resume callback (resumes Kafka consumer)
                    CompletableFuture<BatchResult<String>> future = batcher.submit(value);
                    
                    // Track overflow items
                    future.whenComplete((result, error) -> {
                        if (error != null) {
                            logger.error("Error processing item", error);
                        }
                    });
                }
                
                // APPLICATION RESPONSIBILITY: Commit offsets (if manual commit)
                // kafkaConsumer.commitSync();
            }
        } catch (Exception e) {
            logger.error("Error in consumer loop", e);
        } finally {
            shutdown();
        }
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Graceful shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down...");
        running.set(false);
        
        // LIBRARY RESPONSIBILITY: Gracefully close batcher (processes remaining items)
        batcher.close();
        
        // APPLICATION RESPONSIBILITY: Close Kafka consumer
        kafkaConsumer.close();
        
        logger.info("Shutdown complete. Processed: {}, Overflow: {}", 
            processedCount.get(), overflowCount.get());
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Print metrics and diagnostics.
     */
    public void printMetrics() {
        BatcherDiagnostics diagnostics = batcher.diagnostics();
        MetricsProvider metrics = batcher.getMetricsProvider();
        
        logger.info("=== Vortex Metrics ===");
        logger.info("Queue Depth: {}", diagnostics.getQueueDepth());
        logger.info("Current Batch Size: {}", diagnostics.getCurrentBatchSize());
        logger.info("Current Linger Time: {}", diagnostics.getCurrentLingerTime());
        logger.info("Total Submitted: {}", metrics.getTotalSubmitted());
        logger.info("Total Succeeded: {}", metrics.getTotalSucceeded());
        logger.info("Total Failed: {}", metrics.getTotalFailed());
        logger.info("Total Backpressure Rejected: {}", metrics.getTotalBackpressureRejected());
        logger.info("Total Backpressure Dropped: {}", metrics.getTotalBackpressureDropped());
        logger.info("Average Batch Dispatch Latency: {} ms", 
            metrics.getAverageBatchDispatchLatency().toMillis());
    }
    
    /**
     * Main method for demonstration.
     * In a real Spring application, this would be a @Component or @Service.
     */
    public static void main(String[] args) {
        // APPLICATION RESPONSIBILITY: Configure and create Kafka consumer
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "vortex-example-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "true");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(KAFKA_TOPIC));
        
        // Create and run example
        KafkaConsumerBackpressureExample example = new KafkaConsumerBackpressureExample(consumer);
        
        // Print metrics periodically
        Thread metricsThread = new Thread(() -> {
            while (example.running.get()) {
                try {
                    Thread.sleep(5000);
                    example.printMetrics();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        metricsThread.setDaemon(true);
        metricsThread.start();
        
        // Run consumer
        example.run();
    }
}

