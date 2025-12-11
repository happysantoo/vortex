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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example demonstrating Vortex Micro-Batching Library with Kafka Consumer
 * and application-level overflow handling.
 * 
 * <p>This example shows:
 * <ul>
 *   <li>How to integrate Vortex with Kafka consumer</li>
 *   <li>How to use RejectStrategy for backpressure signaling</li>
 *   <li>How to implement application-level overflow handling</li>
 *   <li>How to pause/resume Kafka consumer based on backpressure</li>
 *   <li>Clear separation of application vs library responsibilities</li>
 * </ul>
 * 
 * <p><b>Application Responsibilities:</b>
 * <ul>
 *   <li>Kafka consumer setup and configuration</li>
 *   <li>Polling Kafka for records</li>
 *   <li>Overflow storage and replay logic</li>
 *   <li>Pausing/resuming consumer partitions</li>
 *   <li>Monitoring backpressure state transitions</li>
 *   <li>Business logic (processing items)</li>
 *   <li>Error handling and logging</li>
 * </ul>
 * 
 * <p><b>Library Responsibilities:</b>
 * <ul>
 *   <li>Batching items efficiently</li>
 *   <li>Detecting backpressure (queue depth)</li>
 *   <li>Signaling backpressure (rejecting items)</li>
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
    private static final int OVERFLOW_CAPACITY = 5000; // Application-managed overflow capacity
    
    // Application state
    private final KafkaConsumer<String, String> kafkaConsumer;
    private final MicroBatcher<String> batcher;
    private final Queue<String> overflowQueue; // Application-managed overflow storage
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean backpressureActive = new AtomicBoolean(false);
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong overflowCount = new AtomicLong(0);
    private final ScheduledExecutorService overflowMonitor;
    
    public KafkaConsumerBackpressureExample(KafkaConsumer<String, String> kafkaConsumer) {
        this.kafkaConsumer = kafkaConsumer;
        this.overflowQueue = new LinkedBlockingQueue<>(OVERFLOW_CAPACITY);
        this.batcher = createMicroBatcher();
        this.overflowMonitor = startOverflowMonitoring();
    }
    
    /**
     * Creates and configures the MicroBatcher with backpressure support.
     * 
     * <p><b>Library Responsibility:</b> The library detects and signals backpressure.
     * The application handles overflow storage and replay.
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
        
        // Backpressure Strategy: Library signals backpressure by rejecting items
        // LIBRARY RESPONSIBILITY: Signals backpressure (application handles overflow)
        BackpressureStrategy<String> strategy = new RejectStrategy<>(BACKPRESSURE_THRESHOLD);
        
        // Configure backpressure in config
        BatcherConfig configWithBackpressure = BatcherConfig.builder()
            .batchSize(BATCH_SIZE)
            .lingerTime(LINGER_TIME)
            .maxQueueSize(MAX_QUEUE_SIZE)
            .backpressureProvider(backpressureProvider)
            .backpressureStrategy(strategy)
            .build();
        
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Create batcher with backpressure support
        // LIBRARY RESPONSIBILITY: Manages batching and backpressure signaling
        return new MicroBatcher<>(backend, configWithBackpressure, meterRegistry);
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Start monitoring backpressure and managing overflow.
     * This replaces the library's lifecycle monitoring that was removed.
     */
    private ScheduledExecutorService startOverflowMonitoring() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "overflow-monitor");
                t.setDaemon(true);
                return t;
            }
        );
        
        // Monitor backpressure state and manage overflow replay
        monitor.scheduleAtFixedRate(() -> {
            try {
                double backpressureLevel = batcher.diagnostics().getQueueDepth() / (double) MAX_QUEUE_SIZE;
                boolean wasActive = backpressureActive.get();
                boolean isActive = backpressureLevel >= BACKPRESSURE_THRESHOLD;
                
                if (!wasActive && isActive) {
                    // Entering backpressure: pause Kafka consumer
                    backpressureActive.set(true);
                    logger.warn("Backpressure detected (level: {:.2f}) - pausing Kafka consumer", backpressureLevel);
                    pauseKafkaConsumer();
                } else if (wasActive && !isActive) {
                    // Exiting backpressure: resume Kafka consumer and start replay
                    backpressureActive.set(false);
                    logger.info("Backpressure resolved (level: {:.2f}) - resuming Kafka consumer", backpressureLevel);
                    resumeKafkaConsumer();
                    replayOverflow();
                } else if (isActive && !overflowQueue.isEmpty()) {
                    // Still in backpressure but may be able to replay some items
                    replayOverflow();
                }
            } catch (Exception e) {
                logger.error("Error in overflow monitoring", e);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        
        return monitor;
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Replay items from overflow when capacity is available.
     */
    private void replayOverflow() {
        int replayed = 0;
        int maxReplayPerCycle = 100; // Limit replay rate
        
        while (!overflowQueue.isEmpty() && replayed < maxReplayPerCycle) {
            // Check if we can accept more items
            double backpressureLevel = batcher.diagnostics().getQueueDepth() / (double) MAX_QUEUE_SIZE;
            if (backpressureLevel >= BACKPRESSURE_THRESHOLD) {
                // Still under pressure, stop replaying
                break;
            }
            
            String item = overflowQueue.poll();
            if (item != null) {
                try {
                    batcher.submit(item);
                    overflowCount.decrementAndGet();
                    replayed++;
                } catch (Exception e) {
                    // If submission fails, put item back
                    logger.warn("Failed to replay item, putting back in overflow", e);
                    overflowQueue.offer(item);
                    break;
                }
            } else {
                break;
            }
        }
        
        if (replayed > 0) {
            logger.debug("Replayed {} items from overflow", replayed);
        }
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
     * Called by application when backpressure is detected.
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
     * Called by application when backpressure resolves.
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
        logger.info("Starting Kafka consumer with application-level overflow handling");
        
        try {
            while (running.get()) {
                // APPLICATION RESPONSIBILITY: Poll Kafka for records
                ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                
                if (records.isEmpty()) {
                    continue;
                }
                
                logger.debug("Polled {} records from Kafka", records.count());
                
                // APPLICATION RESPONSIBILITY: Extract and submit items to batcher
                // LIBRARY RESPONSIBILITY: Handles batching and signals backpressure
                for (ConsumerRecord<String, String> record : records) {
                    String value = record.value();
                    
                    try {
                        // Try to submit to batcher
                        CompletableFuture<BatchResult<String>> future = batcher.submit(value);
                        
                        // Handle rejection (backpressure signaled)
                        future.whenComplete((result, error) -> {
                            if (error != null) {
                                if (error.getCause() instanceof ItemRejectedException) {
                                    // LIBRARY SIGNALED BACKPRESSURE: Application handles overflow
                                    handleBackpressureRejection(value);
                                } else {
                                    logger.error("Error processing item", error);
                                }
                            }
                        });
                    } catch (Exception e) {
                        // Handle synchronous rejection
                        if (e.getCause() instanceof ItemRejectedException) {
                            handleBackpressureRejection(value);
                        } else {
                            logger.error("Error submitting item", e);
                        }
                    }
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
     * APPLICATION RESPONSIBILITY: Handle backpressure rejection by storing to overflow.
     * This is called when the library signals backpressure by rejecting an item.
     */
    private void handleBackpressureRejection(String item) {
        if (overflowQueue.offer(item)) {
            overflowCount.incrementAndGet();
            logger.debug("Item stored to overflow (overflow size: {})", overflowQueue.size());
        } else {
            // Overflow is full - application decides what to do
            // Options: log, alert, send to dead letter queue, drop, etc.
            logger.error("Overflow storage is full, dropping item: {}", item);
            // In production, you might want to:
            // - Send to dead letter queue
            // - Alert monitoring system
            // - Retry with exponential backoff
        }
    }
    
    /**
     * APPLICATION RESPONSIBILITY: Graceful shutdown.
     */
    public void shutdown() {
        logger.info("Shutting down...");
        running.set(false);
        
        // Stop overflow monitoring
        overflowMonitor.shutdown();
        try {
            if (!overflowMonitor.awaitTermination(1, TimeUnit.SECONDS)) {
                overflowMonitor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            overflowMonitor.shutdownNow();
        }
        
        // Replay remaining overflow items before shutdown
        logger.info("Replaying {} remaining overflow items", overflowQueue.size());
        while (!overflowQueue.isEmpty()) {
            String item = overflowQueue.poll();
            if (item != null) {
                try {
                    batcher.submit(item);
                } catch (Exception e) {
                    logger.warn("Failed to replay item during shutdown: {}", item, e);
                }
            }
        }
        
        // Wait for batcher to process remaining items
        try {
            batcher.awaitCompletion(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // LIBRARY RESPONSIBILITY: Gracefully close batcher (processes remaining items)
        batcher.close();
        
        // APPLICATION RESPONSIBILITY: Close Kafka consumer
        kafkaConsumer.close();
        
        logger.info("Shutdown complete. Processed: {}, Overflow handled: {}", 
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
        logger.info("Overflow Queue Size: {}", overflowQueue.size());
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
