package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import com.vajrapulse.vortex.results.SuccessEvent
import com.vajrapulse.vortex.results.FailureEvent
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherErrorHandlingSpec extends Specification {

    def "should mark items as failed when backend throws exception"() {
        given:
        def error = new RuntimeException("backend error")
        Backend<String> backend = { batch -> throw error }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def future = batcher.submitAsync("item")
        def futureResult = future.get(2, TimeUnit.SECONDS)

        then:
        futureResult instanceof ItemResult.Failure
        futureResult.error == error

        cleanup:
        batcher?.close()
    }

    def "should handle mixed success and failure results"() {
        given:
        Backend<String> backend = { batch ->
            def successes = []
            def failures = []
            batch.eachWithIndex { item, index ->
                if (index % 2 == 0) {
                    successes.add(new SuccessEvent<>(item))
                } else {
                    failures.add(new FailureEvent<>(item, new RuntimeException("error")))
                }
            }
            new BatchResult<>(successes, failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(4)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def futures = []
        for (int i = 0; i < 4; i++) {
            futures.add(batcher.submitAsync("item-${i}"))
        }
        def collectedResults = futures.collect { future -> future.get(2, TimeUnit.SECONDS) }

        then:
        collectedResults.size() == 4
        collectedResults.findAll { itemResult -> itemResult instanceof ItemResult.Success }.size() == 2
        collectedResults.findAll { itemResult -> itemResult instanceof ItemResult.Failure }.size() == 2

        cleanup:
        batcher?.close()
    }

    def "should handle all failures from backend"() {
        given:
        def error = new RuntimeException("all failed")
        Backend<String> backend = failingBackend(error)
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def futures = []
        for (int i = 0; i < 3; i++) {
            futures.add(batcher.submitAsync("item-${i}"))
        }
        def collectedResults = futures.collect { future -> future.get(2, TimeUnit.SECONDS) }

        then:
        collectedResults.every { itemResult -> itemResult instanceof ItemResult.Failure }
        collectedResults.every { itemResult -> itemResult.error == error }

        cleanup:
        batcher?.close()
    }

    def "should not crash batcher on exception during batch processing"() {
        given:
        Backend<String> backend = { batch -> throw new RuntimeException("processing error") }
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(100)
        def submitResult = batcher.submit("item-2") // Should still work

        then:
        submitResult instanceof ItemResult.Success
        !batcher.isClosed()

        cleanup:
        batcher?.close()
    }

    def "should not break processing on callback exception"() {
        given:
        def callbackError = new RuntimeException("callback error")
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1", { itemResult -> throw callbackError })
        Thread.sleep(100)
        def submitResult = batcher.submit("item-2") // Should still work

        then:
        submitResult instanceof ItemResult.Success

        cleanup:
        batcher?.close()
    }

    def "should propagate errors to callbacks"() {
        given:
        def error = new RuntimeException("backend error")
        def callbackResult = null
        def callbackLatch = new CountDownLatch(1)
        Backend<String> backend = failingBackend(error)
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item", { itemResult ->
            callbackResult = itemResult
            callbackLatch.countDown()
        })
        callbackLatch.await(2, TimeUnit.SECONDS)

        then:
        callbackResult instanceof ItemResult.Failure
        callbackResult.error == error

        cleanup:
        batcher?.close()
    }

    def "should propagate errors to CompletableFuture"() {
        given:
        def error = new RuntimeException("backend error")
        Backend<String> backend = failingBackend(error)
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def future = batcher.submitAsync("item")
        def futureResult = future.get(2, TimeUnit.SECONDS)

        then:
        futureResult instanceof ItemResult.Failure
        futureResult.error == error

        cleanup:
        batcher?.close()
    }
}

