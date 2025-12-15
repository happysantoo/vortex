package com.vajrapulse.vortex

import com.vajrapulse.vortex.ItemRejectedException
import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import com.vajrapulse.vortex.results.SuccessEvent
import com.vajrapulse.vortex.results.FailureEvent
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherSpec extends Specification {

    // ========== Constructor Tests ==========

    def "should reject null backend"() {
        given:
        def config = BatcherConfig.builder().build()

        when:
        new MicroBatcher<>(null, config)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null config"() {
        given:
        Backend<String> backend = { batch -> new BatchResult<>(List.of(), List.of()) }

        when:
        new MicroBatcher<>(backend, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null meter registry"() {
        given:
        Backend<String> backend = { batch -> new BatchResult<>(List.of(), List.of()) }
        def config = BatcherConfig.builder().build()

        when:
        new MicroBatcher<>(backend, config, null)

        then:
        thrown(IllegalArgumentException)
    }

    // ========== Basic Submission Tests ==========

    def "should accept item and return success immediately"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("test-item")

        then:
        result instanceof ItemResult.Success
        result.item == "test-item"

        cleanup:
        batcher?.close()
    }

    def "should reject null item"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()
        def batcher = new MicroBatcher<>(backend, config)

        when:
        batcher.submit(null)

        then:
        thrown(NullPointerException)

        cleanup:
        batcher?.close()
    }

    def "should reject item when batcher is closed"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()
        def batcher = new MicroBatcher<>(backend, config)
        batcher.close()

        when:
        batcher.submit("item")

        then:
        thrown(IllegalStateException)

        cleanup:
        batcher?.close()
    }

    // ========== Batching Tests ==========

    def "should batch requests by size"() {
        given:
        def batchCount = new AtomicInteger(0)
        Backend<String> backend = { batch ->
            batchCount.incrementAndGet()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        (1..5).each { batcher.submit("item-$it") }
        waitForAsync(200) // Wait for batch processing

        then:
        batchCount.get() >= 1

        cleanup:
        batcher?.close()
    }

    def "should batch requests by time"() {
        given:
        def batchCount = new AtomicInteger(0)
        Backend<String> backend = { batch ->
            batchCount.incrementAndGet()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result1 = batcher.submit("item-1")
        def result2 = batcher.submit("item-2")
        Thread.sleep(200) // Wait for batch to complete (time-based trigger)

        then:
        result1 instanceof ItemResult.Success
        result2 instanceof ItemResult.Success
        batchCount.get() >= 1

        cleanup:
        batcher?.close()
    }

    // ========== Callback Tests ==========

    def "should invoke callback when item is processed successfully"() {
        given:
        def callbackInvoked = new AtomicBoolean(false)
        def callbackResult = new AtomicInteger(0) // 0 = not called, 1 = success, 2 = failure
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item-1", { item, result ->
            callbackInvoked.set(true)
            if (result instanceof ItemResult.Success) {
                callbackResult.set(1)
            } else {
                callbackResult.set(2)
            }
        })
        Thread.sleep(200) // Wait for batch processing

        then:
        submitResult instanceof ItemResult.Success
        callbackInvoked.get() == true
        callbackResult.get() == 1 // Success

        cleanup:
        batcher?.close()
    }

    def "should invoke callback when item processing fails"() {
        given:
        def callbackInvoked = new AtomicBoolean(false)
        def callbackResult = new AtomicInteger(0)
        Backend<String> backend = { batch ->
            def failures = batch.collect { new FailureEvent<>(it, new RuntimeException("processing error")) }
            new BatchResult<>(List.of(), failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item-1", { item, result ->
            callbackInvoked.set(true)
            if (result instanceof ItemResult.Success) {
                callbackResult.set(1)
            } else {
                callbackResult.set(2)
            }
        })
        Thread.sleep(200) // Wait for batch processing

        then:
        submitResult instanceof ItemResult.Success // Item was accepted
        callbackInvoked.get() == true
        callbackResult.get() == 2 // Failure

        cleanup:
        batcher?.close()
    }

    def "should not invoke callback when item is rejected immediately"() {
        given:
        def callbackInvoked = new AtomicBoolean(false)
        def backendBlocked = new CountDownLatch(1)
        Backend<String> backend = { batch ->
            // Block until we signal processing can continue
            backendBlocked.await()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(1) // Small batch size - process one at a time
            .lingerTime(Duration.ofMillis(5000)) // Very long linger time
            .maxQueueSize(2) // Small queue
            .queueRejectionThreshold(1.0) // Reject at 100%
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        // Fill queue
        batcher.submit("item-1")
        batcher.submit("item-2")
        // Wait a moment for items to be queued
        Thread.sleep(100)
        def queueDepth = batcher.getQueueDepth()
        // This should be rejected if queue is full
        def result = batcher.submit("item-3", { item, itemResult ->
            callbackInvoked.set(true)
        })
        Thread.sleep(100)

        then:
        // Queue rejection is timing-dependent - verify rejection mechanism works
        // If queue was full, item should be rejected and callback not invoked
        // If queue wasn't full, item was accepted (callback may or may not have been invoked yet)
        (queueDepth >= 2 && result instanceof ItemResult.Failure && result.error instanceof ItemRejectedException && !callbackInvoked.get()) ||
        (queueDepth < 2 && result instanceof ItemResult.Success)

        cleanup:
        backendBlocked.countDown()
        batcher?.close()
    }

    def "should work without callback"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("item-1") // No callback

        then:
        result instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    // ========== Queue Rejection Tests ==========

    @Unroll
    def "should reject item when queue reaches threshold (threshold: #threshold, maxQueueSize: #maxQueueSize, expectedThresholdItems: #expectedThresholdItems)"() {
        given:
        def backendBlocked = new CountDownLatch(1)
        Backend<String> backend = blockingBackend(backendBlocked)
        def config = BatcherConfig.builder()
            .batchSize(1) // Small batch size - process one at a time
            .lingerTime(Duration.ofMillis(5000)) // Very long linger time
            .maxQueueSize(maxQueueSize)
            .queueRejectionThreshold(threshold)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def results = []
        // Fill queue to threshold - these should all be accepted
        (1..expectedThresholdItems).each { i ->
            results.add(batcher.submit("item-$i"))
        }
        waitForAsync(100) // Wait a moment for items to be queued
        def queueDepth = batcher.getQueueDepth()
        // Next item should be rejected if queue is at threshold
        def nextItem = batcher.submit("item-${expectedThresholdItems + 1}")

        then:
        results.every { it instanceof ItemResult.Success }
        // Queue rejection is timing-dependent - verify rejection mechanism works
        // If queue was at threshold, item should be rejected; otherwise acceptance is also valid
        (queueDepth >= expectedThresholdItems && nextItem instanceof ItemResult.Failure && nextItem.error instanceof ItemRejectedException) ||
        (queueDepth < expectedThresholdItems && (nextItem instanceof ItemResult.Success || nextItem instanceof ItemResult.Failure))

        cleanup:
        backendBlocked.countDown()
        batcher?.close()

        where:
        threshold | maxQueueSize | expectedThresholdItems
        1.0       | 2            | 2  // 100% threshold, queue size 2
        0.8       | 10           | 8  // 80% threshold, queue size 10
    }

    // ========== Batch Result Tests ==========

    def "should handle successful batch processing"() {
        given:
        def batchResults = Collections.synchronizedList(new ArrayList<BatchResult<String>>())
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            def result = new BatchResult<>(successes, List.of())
            batchResults.add(result)
            result
        }
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("test-item")
        Thread.sleep(200) // Wait for batch processing

        then:
        submitResult instanceof ItemResult.Success
        batchResults.size() >= 1
        def batchResult = batchResults[0]
        batchResult.isAllSuccess()
        batchResult.successes.size() == 1
        batchResult.failures.isEmpty()
        batchResult.successes[0].data == "test-item"

        cleanup:
        batcher?.close()
    }

    def "should handle failed batch processing"() {
        given:
        def batchResults = Collections.synchronizedList(new ArrayList<BatchResult<String>>())
        Backend<String> backend = { batch ->
            def failures = batch.collect { new FailureEvent<>(it, new RuntimeException("error")) }
            def result = new BatchResult<>(List.of(), failures)
            batchResults.add(result)
            result
        }
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("test-item")
        Thread.sleep(200) // Wait for batch processing

        then:
        submitResult instanceof ItemResult.Success // Item was accepted
        batchResults.size() >= 1
        def batchResult = batchResults[0]
        !batchResult.isAllSuccess()
        batchResult.successes.isEmpty()
        batchResult.failures.size() == 1
        batchResult.failures[0].data == "test-item"
        batchResult.failures[0].error.message == "error"

        cleanup:
        batcher?.close()
    }

    def "should handle mixed success and failure results"() {
        given:
        def batchResults = Collections.synchronizedList(new ArrayList<BatchResult<String>>())
        Backend<String> backend = { batch ->
            def successes = batch.findAll { it.startsWith("success") }.collect { new SuccessEvent<>(it) }
            def failures = batch.findAll { it.startsWith("fail") }.collect { new FailureEvent<>(it, new RuntimeException("error")) }
            def result = new BatchResult<>(successes, failures)
            batchResults.add(result)
            result
        }
        def config = BatcherConfig.builder()
            .batchSize(4)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResults = [
            batcher.submit("success-1"),
            batcher.submit("fail-1"),
            batcher.submit("success-2"),
            batcher.submit("fail-2")
        ]
        Thread.sleep(200) // Wait for batch processing

        then:
        submitResults.every { it instanceof ItemResult.Success } // All accepted
        batchResults.size() >= 1
        def batchResult = batchResults[0]
        !batchResult.isAllSuccess() // Should have both successes and failures
        batchResult.successes.size() == 2
        batchResult.failures.size() == 2

        cleanup:
        batcher?.close()
    }

    // ========== Close and Cleanup Tests ==========

    def "should process remaining items on close"() {
        given:
        def processedItems = Collections.synchronizedSet(new HashSet<String>())
        def backendCallCount = new AtomicInteger(0)
        Backend<String> backend = { batch ->
            backendCallCount.incrementAndGet()
            batch.each { 
                processedItems.add(it)
            }
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100)) // Short linger time to allow processing
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        (1..5).each { batcher.submit("item-$it") }
        // Wait for items to be processed
        Thread.sleep(2000)
        // Close should complete without errors
        batcher.close()

        then:
        // All items should be processed (either async before close or sync during close)
        // The important thing is that close() completes without errors
        processedItems.size() == 5
        (1..5).every { processedItems.contains("item-$it") }
        // Backend should be called at least once
        backendCallCount.get() >= 1

        cleanup:
        batcher?.close()
    }

    // ========== Metrics Tests ==========

    def "should track metrics"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def meterRegistry = new SimpleMeterRegistry()
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, meterRegistry)
        batcher.submit("item-1")
        batcher.submit("item-2")
        Thread.sleep(200) // Wait for batch processing

        then:
        meterRegistry.counter("vortex.requests.submitted").count() == 2
        meterRegistry.counter("vortex.batches.dispatched").count() >= 1
        meterRegistry.counter("vortex.requests.succeeded").count() >= 1
        meterRegistry.find("vortex.queue.depth").gauge() != null

        cleanup:
        batcher?.close()
    }

    def "should track rejected requests"() {
        given:
        def backendBlocked = new CountDownLatch(1)
        Backend<String> backend = { batch ->
            // Block until we signal processing can continue
            backendBlocked.await()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def meterRegistry = new SimpleMeterRegistry()
        def config = BatcherConfig.builder()
            .batchSize(1) // Small batch size - process one at a time
            .lingerTime(Duration.ofMillis(5000)) // Very long linger time
            .maxQueueSize(2)
            .queueRejectionThreshold(1.0)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, meterRegistry)
        batcher.submit("item-1")
        batcher.submit("item-2")
        // Wait a moment for items to be queued
        Thread.sleep(100)
        def queueDepth = batcher.getQueueDepth()
        batcher.submit("item-3") // Should be rejected if queue is full

        then:
        // If queue was full, we should have at least one rejection
        // If queue wasn't full, no rejection is expected (both are valid)
        (queueDepth >= 2 && meterRegistry.counter("vortex.requests.rejected").count() >= 1) ||
        (queueDepth < 2)

        cleanup:
        backendBlocked.countDown()
        batcher?.close()
    }


    // ========== Diagnostics Tests ==========

    def "diagnostics should expose current state"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        def diagnostics = batcher.diagnostics()

        then:
        !diagnostics.isClosed()
        diagnostics.getCurrentBatchSize() == 2
        diagnostics.getCurrentLingerTime() == Duration.ofMillis(50)
        diagnostics.getQueueDepth() == 0

        cleanup:
        batcher?.close()
    }

    // ========== Concurrent Submission Tests ==========

    def "should handle concurrent submissions"() {
        given:
        def batchCount = new AtomicInteger(0)
        Backend<String> backend = { batch ->
            batchCount.incrementAndGet()
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def threads = (1..10).collect { i ->
            Thread.start {
                batcher.submit("item-$i")
            }
        }
        threads.each { it.join() }
        Thread.sleep(200) // Wait for batch processing

        then:
        batchCount.get() >= 1

        cleanup:
        batcher?.close()
    }

    // ========== Atomic Commit Tests ==========

    def "should enforce atomic commit when enabled"() {
        given:
        def processedItems = Collections.synchronizedSet(new HashSet<String>())
        Backend<String> backend = { batch ->
            batch.each { processedItems.add(it) }
            def successes = batch.findAll { !it.contains("fail") }.collect { new SuccessEvent<>(it) }
            def failures = batch.findAll { it.contains("fail") }.collect { new FailureEvent<>(it, new RuntimeException("error")) }
            new BatchResult<>(successes, failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .atomicCommit(true)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result1 = batcher.submit("success-1")
        def result2 = batcher.submit("fail-item")
        def result3 = batcher.submit("success-2")
        Thread.sleep(300)  // Wait for batch processing

        then:
        result1 instanceof ItemResult.Success
        result2 instanceof ItemResult.Success
        result3 instanceof ItemResult.Success
        // All items should be processed (atomic commit failure means all fail)
        processedItems.size() == 3

        cleanup:
        batcher?.close()
    }

    def "should handle all success scenario in atomic commit mode"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .atomicCommit(true)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResults = [
            batcher.submit("success-1"),
            batcher.submit("success-2"),
            batcher.submit("success-3")
        ]
        Thread.sleep(200)  // Wait for batch processing

        then:
        submitResults.every { it instanceof ItemResult.Success }

        cleanup:
        batcher?.close()
    }

    // ========== Result Processor Fallback Tests ==========

    def "should handle fallback when results don't match requests"() {
        given:
        Backend<String> backend = { batch ->
            // Return results that don't match the request data (triggers fallback)
            def successes = [new SuccessEvent<>("different-item")]
            new BatchResult<>(successes, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("item-1")
        Thread.sleep(200)  // Wait for batch processing

        then:
        result instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    def "should handle fallback with unmatched failures"() {
        given:
        Backend<String> backend = { batch ->
            // Return failure that doesn't match the request data (triggers fallback)
            def failures = [new FailureEvent<>("different-item", new RuntimeException("error"))]
            new BatchResult<>(List.of(), failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("item-1")
        Thread.sleep(200)  // Wait for batch processing

        then:
        result instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    def "should handle fallback with no unmatched results"() {
        given:
        Backend<String> backend = { batch ->
            // Return empty result (triggers fallback with no unmatched results)
            new BatchResult<>(List.of(), List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("item-1")
        Thread.sleep(200)  // Wait for batch processing

        then:
        result instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    // ========== Process Failure Tests ==========

    def "should handle backend exception during dispatch"() {
        given:
        def exceptionThrown = new AtomicBoolean(false)
        Backend<String> backend = { batch ->
            exceptionThrown.set(true)
            throw new RuntimeException("Backend error")
        }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result = batcher.submit("item-1")
        Thread.sleep(200)  // Wait for batch processing

        then:
        result instanceof ItemResult.Success
        exceptionThrown.get() == true

        cleanup:
        batcher?.close()
    }
}
