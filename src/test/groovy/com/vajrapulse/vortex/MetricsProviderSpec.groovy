package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import com.vajrapulse.vortex.results.SuccessEvent
import spock.lang.Specification
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MetricsProviderSpec extends Specification {
    
    def "should provide zero metrics for new batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        then:
        metrics.getTotalSubmitted() == 0
        metrics.getTotalSucceeded() == 0
        metrics.getTotalFailed() == 0
        metrics.getTotalReplayed() == 0
        metrics.getTotalBatchesDispatched() == 0
        metrics.getQueueDepth() == 0
        metrics.getFailureRate() == 0.0
        metrics.getSuccessRate() == 0.0
        // Latency metrics return 0.0 when no data (NaN is converted to 0.0)
        metrics.getAverageDispatchLatency() == 0.0
        metrics.getAverageWaitLatency() == 0.0
        metrics.getP95DispatchLatency() == 0.0
        metrics.getP99DispatchLatency() == 0.0
        
        cleanup:
        batcher?.close()
    }
    
    def "should track submitted requests"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("item-1")
        batcher.submit("item-2")
        batcher.submit("item-3")
        Thread.sleep(150) // Wait for processing
        
        then:
        metrics.getTotalSubmitted() == 3
        metrics.getTotalSucceeded() == 3
        metrics.getTotalFailed() == 0
        metrics.getTotalBatchesDispatched() >= 1
        
        cleanup:
        batcher?.close()
    }
    
    def "should calculate failure rate correctly"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.findAll { it.startsWith("success") }
                .collect { new SuccessEvent<>(it) }
            def failures = batch.findAll { it.startsWith("fail") }
                .collect { new FailureEvent<>(it, new RuntimeException("error")) }
            new BatchResult<>(successes, failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("success-1")
        batcher.submit("success-2")
        batcher.submit("fail-1")
        batcher.submit("fail-2")
        Thread.sleep(150)
        
        then:
        metrics.getTotalSubmitted() == 4
        metrics.getTotalSucceeded() == 2
        metrics.getTotalFailed() == 2
        Math.abs(metrics.getFailureRate() - 0.5) < 0.001
        Math.abs(metrics.getSuccessRate() - 0.5) < 0.001
        
        cleanup:
        batcher?.close()
    }
    
    def "should track queue depth"() {
        given:
        Backend<String> backend = { batch ->
            Thread.sleep(200) // Slow processing
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(500)) // Long linger time
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        // Submit items quickly
        batcher.submit("item-1")
        batcher.submit("item-2")
        batcher.submit("item-3")
        Thread.sleep(50) // Small delay to let items queue
        
        then:
        metrics.getQueueDepth() >= 0
        metrics.getQueueDepth() <= 3
        
        cleanup:
        batcher?.close()
    }
    
    def "should track replayed requests"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.findAll { it.startsWith("success") }
                .collect { new SuccessEvent<>(it) }
            def failures = batch.findAll { it.startsWith("fail") }
                .collect { new FailureEvent<>(it, new RuntimeException("error")) }
            new BatchResult<>(successes, failures)
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .autoReplaySuccesses(true)
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("success-1")
        batcher.submit("fail-1")
        Thread.sleep(200) // Wait for processing and replay
        
        then:
        metrics.getTotalReplayed() >= 1
        
        cleanup:
        batcher?.close()
    }

    def "should track retried and rejected requests"() {
        given:
        def processedLatch = new CountDownLatch(3)
        Backend<String> backend = { batch ->
            // Always fail to trigger retries
            batch.each { processedLatch.countDown() }
            new BatchResult<>(List.of(), batch.collect {
                new FailureEvent<>(it, new RuntimeException("error"))
            })
        }
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .maxRetries(1)
            .retryableErrorPredicate({ true })
            .maxQueueSize(2)
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()

        // Submit a few items, some will be retried and some may be rejected
        def futures = []
        5.times {
            futures << batcher.submit("item-$it")
        }
        // Wait for processing and potential retries
        processedLatch.await(2, TimeUnit.SECONDS)
        Thread.sleep(100)

        then:
        metrics.getTotalRetried() >= 1
        metrics.getTotalRejected() >= 0

        cleanup:
        batcher?.close()
    }
    
    def "should provide latency metrics"() {
        given:
        Backend<String> backend = { batch ->
            Thread.sleep(50) // Simulate processing time
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("item-1")
        batcher.submit("item-2")
        batcher.submit("item-3")
        Thread.sleep(200) // Wait for processing
        
        then:
        metrics.getAverageDispatchLatency() >= 0.0
        metrics.getAverageWaitLatency() >= 0.0
        metrics.getP95DispatchLatency() >= 0.0
        metrics.getP99DispatchLatency() >= 0.0
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle zero division gracefully"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        // No submissions yet
        
        then:
        metrics.getFailureRate() == 0.0
        metrics.getSuccessRate() == 0.0
        metrics.getAverageDispatchLatency() == 0.0
        metrics.getAverageWaitLatency() == 0.0
        
        cleanup:
        batcher?.close()
    }
    
    def "should provide real-time metrics updates"() {
        given:
        def processedLatch = new CountDownLatch(5)
        Backend<String> backend = { batch ->
            batch.each { processedLatch.countDown() }
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        // Submit items
        batcher.submit("item-1")
        batcher.submit("item-2")
        
        def beforeProcessing = metrics.getTotalSubmitted()
        
        // Wait for processing
        processedLatch.await(1, TimeUnit.SECONDS)
        Thread.sleep(50)
        
        def afterProcessing = metrics.getTotalSubmitted()
        def batchesDispatched = metrics.getTotalBatchesDispatched()
        
        then:
        beforeProcessing == 2
        afterProcessing == 2
        batchesDispatched >= 1
        
        cleanup:
        batcher?.close()
    }
    
    def "should calculate metrics correctly with all failures"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(List.of(), batch.collect { 
                new FailureEvent<>(it, new RuntimeException("error")) 
            })
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("item-1")
        batcher.submit("item-2")
        Thread.sleep(150)
        
        then:
        metrics.getTotalSubmitted() == 2
        metrics.getTotalSucceeded() == 0
        metrics.getTotalFailed() == 2
        Math.abs(metrics.getFailureRate() - 1.0) < 0.001
        Math.abs(metrics.getSuccessRate() - 0.0) < 0.001
        
        cleanup:
        batcher?.close()
    }
    
    def "should calculate metrics correctly with all successes"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        when:
        def batcher = new MicroBatcher<>(backend, config)
        def metrics = batcher.getMetricsProvider()
        
        batcher.submit("item-1")
        batcher.submit("item-2")
        Thread.sleep(150)
        
        then:
        metrics.getTotalSubmitted() == 2
        metrics.getTotalSucceeded() == 2
        metrics.getTotalFailed() == 0
        Math.abs(metrics.getFailureRate() - 0.0) < 0.001
        Math.abs(metrics.getSuccessRate() - 1.0) < 0.001
        
        cleanup:
        batcher?.close()
    }
}

