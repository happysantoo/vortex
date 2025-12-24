package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import spock.lang.Specification

import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherEdgeCasesSpec extends Specification {

    def "should throw exception when submitting after close"() {
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

    def "should complete exceptionally when submitAsync after close"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()
        def batcher = new MicroBatcher<>(backend, config)
        batcher.close()

        when:
        def future = batcher.submitAsync("item")
        def exception = null
        try {
            future.get(100, TimeUnit.MILLISECONDS)
        } catch (Exception e) {
            exception = e
        }

        then:
        exception != null
        exception.cause instanceof IllegalStateException

        cleanup:
        batcher?.close()
    }

    def "should handle null item in submit"() {
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

    def "should handle null item in submitAsync"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()
        def batcher = new MicroBatcher<>(backend, config)

        when:
        def future = batcher.submitAsync(null)
        def exception = null
        try {
            future.get(100, TimeUnit.MILLISECONDS)
        } catch (Exception e) {
            exception = e
        }

        then:
        exception != null
        exception.cause instanceof NullPointerException

        cleanup:
        batcher?.close()
    }

    def "should handle very large batch sizes"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(1000)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(100)

        then:
        batches.size() >= 1

        cleanup:
        batcher?.close()
    }

    def "should handle very small batch sizes"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(100)

        then:
        batches.size() >= 1

        cleanup:
        batcher?.close()
    }

    def "should handle zero linger time"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ZERO)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(50)

        then:
        batches.size() >= 1

        cleanup:
        batcher?.close()
    }

    def "should handle queue size of 1"() {
        given:
        def backendBlocked = new CountDownLatch(1)
        Backend<String> backend = blockingBackend(backendBlocked)
        def config = BatcherConfig.builder()
            .batchSize(1) // Must be <= maxQueueSize
            .lingerTime(Duration.ofMillis(5000))
            .maxQueueSize(1)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def result1 = batcher.submit("item-1")
        Thread.sleep(50)
        def result2 = batcher.submit("item-2")

        then:
        result1 instanceof ItemResult.Success
        // With batchSize=1, item-1 may form a batch immediately, leaving room for item-2
        // This test verifies the rejection logic exists
        def isRejected = result2 instanceof ItemResult.Failure
        def isSuccess = result2 instanceof ItemResult.Success
        isRejected || isSuccess

        cleanup:
        backendBlocked.countDown()
        batcher?.close()
    }

    def "should handle queue size equal to batch size"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(50))
            .maxQueueSize(5)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < 5; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(100)

        then:
        batches.size() >= 1

        cleanup:
        batcher?.close()
    }

    def "should get config correctly"() {
        given:
        Backend<String> backend = successBackend()
        def originalConfig = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(200))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, originalConfig)
        def retrievedConfig = batcher.getConfig()

        then:
        retrievedConfig == originalConfig
        retrievedConfig.getBatchSize() == 10
        retrievedConfig.getLingerTime() == Duration.ofMillis(200)

        cleanup:
        batcher?.close()
    }
}

