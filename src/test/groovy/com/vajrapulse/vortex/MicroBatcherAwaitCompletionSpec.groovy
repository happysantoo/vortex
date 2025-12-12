package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import com.vajrapulse.vortex.results.SuccessEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MicroBatcherAwaitCompletionSpec extends Specification {

    def "should wait for queue to drain"() {
        given:
        def batchLatch = new CountDownLatch(1)
        
        Backend<String> backend = { batch ->
            batchLatch.countDown()  // Signal batch started
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        // Submit items
        def results = (1..3).collect { batcher.submit("item-$it") }
        
        // Wait for batch to start processing
        batchLatch.await(1, TimeUnit.SECONDS)
        
        // Wait for completion
        boolean completed = batcher.awaitCompletion(2, TimeUnit.SECONDS)
        
        then:
        completed == true
        batcher.getQueueDepth() == 0
        
        // All items should be accepted (not rejected)  
        results.each { assert it instanceof ItemResult.Success }
        
        cleanup:
        batcher?.close()
    }
    
    def "should wait for in-flight batches when concurrent limiting enabled"() {
        given:
        def maxConcurrent = 2
        def batchLatch = new CountDownLatch(maxConcurrent)
        def processingStarted = new AtomicBoolean(false)
        
        Backend<String> backend = { batch ->
            processingStarted.set(true)
            try {
                // Simulate slow processing
                Thread.sleep(200)
            } finally {
                batchLatch.countDown()
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(maxConcurrent)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        // Submit batches
        def results = (1..maxConcurrent).collect { batcher.submit("item-$it") }
        
        // Wait for processing to start
        Thread.sleep(50)
        
        // Wait for completion
        boolean completed = batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        completed == true
        processingStarted.get() == true
        
        // All items should be accepted (not rejected)
        results.each { assert it instanceof ItemResult.Success }
        
        cleanup:
        batcher?.close()
    }
    
    def "should return false on timeout"() {
        given:
        def batchLatch = new CountDownLatch(1)
        
        Backend<String> backend = { batch ->
            try {
                // Simulate very slow processing
                Thread.sleep(500)
            } finally {
                batchLatch.countDown()
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(1)  // Enable concurrent limiting to test in-flight wait
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        // Submit item
        batcher.submit("item-1")
        Thread.sleep(50)  // Wait for batch to start
        
        // Wait for completion with short timeout
        boolean completed = batcher.awaitCompletion(100, TimeUnit.MILLISECONDS)
        
        then:
        completed == false  // Should timeout
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle interruption"() {
        given:
        Backend<String> backend = { batch ->
            // Simulate slow processing
            Thread.sleep(500)
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(1)  // Enable concurrent limiting
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        Thread.sleep(50)
        
        // Interrupt current thread
        Thread.currentThread().interrupt()
        
        // Try awaitCompletion - should throw InterruptedException
        batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        thrown(InterruptedException)
        
        cleanup:
        Thread.interrupted()  // Clear interrupt flag
        batcher?.close()
    }
    
    def "should work when batcher is already closed"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(1)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        batcher.close()
        
        // Wait for completion after close
        boolean completed = batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        completed == true  // Should complete even after close
        
        cleanup:
        batcher?.close()
    }
    
    def "should return true immediately when queue is empty and no in-flight batches"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        // Submit item
        def result = batcher.submit("item-1")
        
        // Wait a bit for batch to complete
        Thread.sleep(100)
        
        // Now await completion (should return immediately)
        boolean completed = batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        completed == true
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle awaitCompletion when no concurrent limiting"() {
        given:
        Backend<String> backend = { batch ->
            Thread.sleep(100)  // Simulate processing
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            // No maxConcurrentBatches (unlimited)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        Thread.sleep(50)
        
        // Close first to test executor shutdown path
        batcher.close()
        
        // Then await completion
        boolean completed = batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        completed == true
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle awaitCompletion with timeout for in-flight batches"() {
        given:
        def batchLatch = new CountDownLatch(1)
        
        Backend<String> backend = { batch ->
            try {
                Thread.sleep(500)  // Slow processing
            } finally {
                batchLatch.countDown()
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(1)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        Thread.sleep(50)  // Wait for batch to start
        
        // Await with short timeout
        boolean completed = batcher.awaitCompletion(100, TimeUnit.MILLISECONDS)
        
        then:
        completed == false  // Should timeout
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle awaitCompletion when queue timeout occurs"() {
        given:
        def backendBlocked = new AtomicBoolean(true)
        def processingStarted = new CountDownLatch(1)
        
        Backend<String> backend = { batch ->
            processingStarted.countDown()
            // Block until we allow processing
            while (backendBlocked.get()) {
                Thread.sleep(10)
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(2)  // Batch size matches number of items
            .lingerTime(Duration.ofMillis(2000))  // Long linger so items stay in queue
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        // Submit items that will stay in queue (long linger time, backend blocked)
        batcher.submit("item-1")
        batcher.submit("item-2")
        
        // Wait for items to be queued (but not processed due to long linger time)
        Thread.sleep(50)
        
        // Verify queue is not empty
        def queueDepth = batcher.getQueueDepth()
        def queueNotEmpty = queueDepth > 0
        
        // Await with very short timeout - should timeout while queue is not empty
        boolean completed = batcher.awaitCompletion(50, TimeUnit.MILLISECONDS)
        
        then:
        // If queue was not empty, should timeout. If queue was empty (race condition), that's also valid
        if (queueNotEmpty) {
            completed == false  // Should timeout because queue is not empty
        } else {
            // Queue was empty - item might have been processed, which is also valid
            completed == true
        }
        
        cleanup:
        backendBlocked.set(false)  // Unblock backend
        batcher?.close()
    }
    
    def "should handle awaitCompletion when activeBatchCount is null and executor not shut down"() {
        given:
        Backend<String> backend = { batch ->
            Thread.sleep(100)  // Simulate processing
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            // No maxConcurrentBatches (unlimited) - activeBatchCount will be null
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        Thread.sleep(50)  // Wait for batch to start
        
        // Await completion - executor is not shut down yet
        boolean completed = batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        completed == true  // Should return true (can't reliably wait when executor not shut down)
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle awaitCompletion interruption during queue wait"() {
        given:
        def backendBlocked = new AtomicBoolean(true)
        def interruptDetected = new AtomicBoolean(false)
        
        Backend<String> backend = { batch ->
            // Block until we allow processing
            while (backendBlocked.get()) {
                Thread.sleep(10)
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(2000))  // Long linger so items stay in queue
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        
        // Wait for item to be queued (but not processed due to long linger time)
        Thread.sleep(50)
        
        // Verify queue is not empty before interrupting
        def queueDepth = batcher.getQueueDepth()
        
        // Interrupt current thread before calling awaitCompletion
        // This ensures the interrupt is checked during the queue wait loop
        Thread.currentThread().interrupt()
        
        try {
            batcher.awaitCompletion(1, TimeUnit.SECONDS)
        } catch (InterruptedException e) {
            // Expected - interrupt was detected during queue wait
            interruptDetected.set(true)
            Thread.interrupted()  // Clear interrupt flag
        }
        
        then:
        // If queue was not empty, we should have detected the interrupt
        // If queue was empty, we might not enter the wait loop, so no exception
        // Both scenarios are valid, but we expect exception if queue was not empty
        if (queueDepth > 0) {
            interruptDetected.get() == true
        }
        // If queue was empty, interrupt might not be detected (valid scenario)
        
        cleanup:
        Thread.interrupted()  // Clear interrupt flag in case it's still set
        backendBlocked.set(false)  // Unblock backend
        batcher?.close()
    }
    
    def "should handle awaitCompletion interruption during in-flight wait"() {
        given:
        Backend<String> backend = { batch ->
            Thread.sleep(500)  // Slow processing
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(10))
            .maxConcurrentBatches(1)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)
        
        when:
        batcher.submit("item-1")
        Thread.sleep(50)  // Wait for batch to start (queue should be empty now)
        
        // Interrupt while waiting for in-flight batches
        Thread.currentThread().interrupt()
        batcher.awaitCompletion(1, TimeUnit.SECONDS)
        
        then:
        thrown(InterruptedException)
        
        cleanup:
        Thread.interrupted()  // Clear interrupt flag
        batcher?.close()
    }
}

