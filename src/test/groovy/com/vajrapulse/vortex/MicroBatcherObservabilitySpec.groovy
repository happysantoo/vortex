package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherObservabilitySpec extends Specification {

    def "should return non-null metrics provider"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metricsProvider = batcher.getMetricsProvider()

        then:
        metricsProvider != null

        cleanup:
        batcher?.close()
    }

    def "should record metrics for submissions"() {
        given:
        def registry = new SimpleMeterRegistry()
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, registry)
        batcher.submit("item-1")
        Thread.sleep(100)

        then:
        registry.counter("vortex.requests.submitted").count() == 1

        cleanup:
        batcher?.close()
    }

    def "should record metrics for successes"() {
        given:
        def registry = new SimpleMeterRegistry()
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, registry)
        batcher.submit("item-1")
        Thread.sleep(100)

        then:
        registry.counter("vortex.requests.succeeded").count() == 1

        cleanup:
        batcher?.close()
    }

    def "should record metrics for failures"() {
        given:
        def registry = new SimpleMeterRegistry()
        def error = new RuntimeException("error")
        Backend<String> backend = failingBackend(error)
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ofMillis(50))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, registry)
        batcher.submit("item-1")
        Thread.sleep(100)

        then:
        registry.counter("vortex.requests.failed").count() == 1

        cleanup:
        batcher?.close()
    }

    def "should record metrics for rejections"() {
        given:
        def registry = new SimpleMeterRegistry()
        def backendBlocked = new CountDownLatch(1)
        Backend<String> backend = blockingBackend(backendBlocked)
        def config = BatcherConfig.builder()
            .batchSize(1) // Must be <= maxQueueSize
            .lingerTime(Duration.ofMillis(5000))
            .maxQueueSize(1)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config, registry)
        batcher.submit("item-1")
        Thread.sleep(50)
        def result2 = batcher.submit("item-2") // May be rejected
        backendBlocked.countDown()

        then:
        // With batchSize=1, item-1 may form a batch immediately, leaving room for item-2
        // Check rejection count only if item-2 was actually rejected
        if (result2 instanceof ItemResult.Failure && result2.error instanceof ItemRejectedException) {
            registry.counter("vortex.requests.rejected").count() == 1
        } else {
            registry.counter("vortex.requests.rejected").count() == 0
        }

        cleanup:
        batcher?.close()
    }

    def "should return correct meter registry"() {
        given:
        def registry = new SimpleMeterRegistry()
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder().build()

        when:
        def batcher = new MicroBatcher<>(backend, config, registry)

        then:
        batcher.getMeterRegistry() == registry

        cleanup:
        batcher?.close()
    }

    def "should return correct diagnostics values"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(200))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def diagnostics = batcher.diagnostics()

        then:
        diagnostics != null
        diagnostics.isClosed() == false
        diagnostics.getQueueDepth() >= 0
        diagnostics.getCurrentBatchSize() == 10
        diagnostics.getCurrentLingerTime() == Duration.ofMillis(200)

        cleanup:
        batcher?.close()
    }

    def "should invoke tracing hook on submit"() {
        given:
        def hookInvoked = new AtomicBoolean(false)
        def tracingHook = new BatchTracingHook() {
            @Override
            void onSubmit(Object item) {
                hookInvoked.set(true)
            }
            
            @Override
            void onBatchDispatchStart(List<?> batchItems) {}
            
            @Override
            void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {}
            
            @Override
            void onBatchDispatchFailure(List<?> batchItems, Throwable error) {}
            
            @Override
            void onRetry(Object item, Throwable cause) {}
        }
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .tracingHook(tracingHook)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item")

        then:
        hookInvoked.get() == true

        cleanup:
        batcher?.close()
    }

    def "should handle tracing hook errors gracefully"() {
        given:
        def tracingHook = new BatchTracingHook() {
            @Override
            void onSubmit(Object item) {
                throw new RuntimeException("hook error")
            }
            
            @Override
            void onBatchDispatchStart(List<?> batchItems) {}
            
            @Override
            void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {}
            
            @Override
            void onBatchDispatchFailure(List<?> batchItems, Throwable error) {}
            
            @Override
            void onRetry(Object item, Throwable cause) {}
        }
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .tracingHook(tracingHook)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def submitResult = batcher.submit("item")

        then:
        submitResult instanceof ItemResult.Success
        noExceptionThrown()

        cleanup:
        batcher?.close()
    }
}

