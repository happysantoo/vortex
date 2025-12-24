package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.ItemResult
import spock.lang.Specification

import java.time.Duration
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherBasicSubmissionSpec extends Specification {

    def "should accept item and return success immediately"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("test-item")

        then:
        submitResult instanceof ItemResult.Success
        submitResult.item == "test-item"

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

    def "should submit with callback - callback invoked on success"() {
        given:
        def callbackInvoked = new CountDownLatch(1)
        def callbackResult = null
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item", { itemResult ->
            callbackResult = itemResult
            callbackInvoked.countDown()
        })

        then:
        submitResult instanceof ItemResult.Success
        callbackInvoked.await(2, TimeUnit.SECONDS)
        callbackResult instanceof ItemResult.Success
        callbackResult.item == "item"

        cleanup:
        batcher?.close()
    }

    def "should submit with callback - callback invoked on failure"() {
        given:
        def callbackInvoked = new CountDownLatch(1)
        def callbackResult = null
        def error = new RuntimeException("backend error")
        Backend<String> backend = failingBackend(error)
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item", { itemResult ->
            callbackResult = itemResult
            callbackInvoked.countDown()
        })

        then:
        submitResult instanceof ItemResult.Success // Immediate acceptance
        callbackInvoked.await(2, TimeUnit.SECONDS)
        callbackResult instanceof ItemResult.Failure
        callbackResult.item == "item"
        callbackResult.error == error

        cleanup:
        batcher?.close()
    }

    def "should work without callback"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item-1") // No callback

        then:
        submitResult instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    def "should handle multiple submissions in sequence"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResults = []
        for (int i = 0; i < 5; i++) {
            submitResults.add(batcher.submit("item-${i}"))
        }

        then:
        submitResults.size() == 5
        submitResults.every { it instanceof ItemResult.Success }

        cleanup:
        batcher?.close()
    }

    def "should handle concurrent submissions"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResults = Collections.synchronizedList([])
        def threads = []
        
        (1..10).each { i ->
            threads.add(Thread.startVirtualThread {
                submitResults.add(batcher.submit("item-${i}"))
            })
        }
        
        threads.each { it.join() }

        then:
        submitResults.size() == 10
        submitResults.every { it instanceof ItemResult.Success }

        cleanup:
        batcher?.close()
    }

    def "should return correct item in result"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("specific-item")

        then:
        submitResult instanceof ItemResult.Success
        submitResult.item == "specific-item"

        cleanup:
        batcher?.close()
    }
}

