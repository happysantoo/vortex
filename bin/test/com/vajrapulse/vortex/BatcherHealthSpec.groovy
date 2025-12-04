package com.vajrapulse.vortex

import com.vajrapulse.vortex.backpressure.BackpressureProvider
import com.vajrapulse.vortex.backpressure.BackpressureStrategy
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

class BatcherHealthSpec extends Specification {

    def "should return UP for healthy batcher"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(50))
            .maxQueueSize(20)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        when:
        BatcherHealth.HealthStatus status = BatcherHealth.check(batcher)
        
        then:
        status == BatcherHealth.HealthStatus.UP
        
        cleanup:
        batcher?.close()
    }
    
    def "should return DOWN for closed batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(List.of(), List.of())
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        batcher.close()
        
        when:
        BatcherHealth.HealthStatus status = BatcherHealth.check(batcher)
        
        then:
        status == BatcherHealth.HealthStatus.DOWN
    }
    
    def "should return DEGRADED for high failure rate"() {
        given:
        Backend<String> backend = { batch ->
            def failures = batch.collect { new FailureEvent<>(it, new RuntimeException("Fail")) }
            new BatchResult<>(List.of(), failures)
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(50))
            .maxQueueSize(20)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        // Submit items to generate failures
        20.times { i ->
            batcher.submit("item-$i")
        }
        
        // Wait for processing
        Thread.sleep(200)
        
        when:
        BatcherHealth.HealthStatus status = BatcherHealth.check(batcher)
        
        then:
        status == BatcherHealth.HealthStatus.DEGRADED || status == BatcherHealth.HealthStatus.DOWN
        
        cleanup:
        batcher?.close()
    }
    
    def "should return DOWN for very high failure rate"() {
        given:
        Backend<String> backend = { batch ->
            def failures = batch.collect { new FailureEvent<>(it, new RuntimeException("Fail")) }
            new BatchResult<>(List.of(), failures)
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(50))
            .maxQueueSize(10)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        // Submit many items to generate high failure rate
        50.times { i ->
            batcher.submit("item-$i")
        }
        
        // Wait for processing
        Thread.sleep(300)
        
        when:
        BatcherHealth.HealthStatus status = BatcherHealth.check(batcher)
        
        then:
        // With 100% failure rate, should be DOWN
        status == BatcherHealth.HealthStatus.DOWN
        
        cleanup:
        batcher?.close()
    }
    
    def "should use custom thresholds"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .maxQueueSize(20)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        when:
        BatcherHealth.HealthStatus status = BatcherHealth.checkWithThresholds(
            batcher,
            0.05,  // max failure rate: 5%
            0.9    // max queue utilization: 90%
        )
        
        then:
        status == BatcherHealth.HealthStatus.UP
        
        cleanup:
        batcher?.close()
    }
    
    def "should reject invalid thresholds"() {
        given:
        Backend<String> backend = { batch -> new BatchResult<>(List.of(), List.of()) }
        BatcherConfig config = BatcherConfig.builder().batchSize(10).build()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        when:
        BatcherHealth.checkWithThresholds(batcher, -0.1, 0.5)
        
        then:
        thrown(IllegalArgumentException)
        
        when:
        BatcherHealth.checkWithThresholds(batcher, 0.5, 1.1)
        
        then:
        thrown(IllegalArgumentException)
        
        cleanup:
        batcher?.close()
    }
    
    def "should get detailed health info"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .maxQueueSize(20)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        when:
        BatcherHealth.HealthInfo info = BatcherHealth.getHealthInfo(batcher)
        
        then:
        info != null
        info.status() == BatcherHealth.HealthStatus.UP
        !info.closed()
        info.failureRate() >= 0.0
        info.failureRate() <= 1.0
        info.queueDepth() >= 0
        info.maxQueueSize() == 20
        info.totalSubmitted() >= 0
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle null batcher"() {
        when:
        BatcherHealth.check(null)
        
        then:
        thrown(NullPointerException)
    }
    
    def "should test HealthInfo helper methods"() {
        given:
        // Test isHealthy() - UP status
        def healthyInfo = new BatcherHealth.HealthInfo(
            BatcherHealth.HealthStatus.UP,
            false,
            0.0,
            1.0,
            0,
            20,
            0.0,
            10L,
            10L,
            0L
        )
        
        // Test isDegraded() - DEGRADED status
        def degradedInfo = new BatcherHealth.HealthInfo(
            BatcherHealth.HealthStatus.DEGRADED,
            false,
            0.1,
            0.9,
            5,
            20,
            0.25,
            10L,
            9L,
            1L
        )
        
        // Test isDown() - DOWN status
        def downInfo = new BatcherHealth.HealthInfo(
            BatcherHealth.HealthStatus.DOWN,
            true,
            0.5,
            0.5,
            20,
            20,
            1.0,
            10L,
            5L,
            5L
        )
        
        expect:
        healthyInfo.isHealthy()
        !healthyInfo.isDegraded()
        !healthyInfo.isDown()
        
        !degradedInfo.isHealthy()
        degradedInfo.isDegraded()
        !degradedInfo.isDown()
        
        !downInfo.isHealthy()
        !downInfo.isDegraded()
        downInfo.isDown()
    }
    
    def "should get health info with all status types"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(10)
            .maxQueueSize(20)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, new SimpleMeterRegistry())
        
        when:
        BatcherHealth.HealthInfo info = BatcherHealth.getHealthInfo(batcher)
        
        then:
        info != null
        info.status() == BatcherHealth.HealthStatus.UP
        !info.closed()
        info.isHealthy()
        !info.isDegraded()
        !info.isDown()
        info.failureRate() >= 0.0
        info.failureRate() <= 1.0
        info.successRate() >= 0.0
        info.successRate() <= 1.0
        info.queueDepth() >= 0
        info.maxQueueSize() == 20
        info.queueUtilization() >= 0.0
        info.queueUtilization() <= 1.0
        info.totalSubmitted() >= 0
        info.totalSucceeded() >= 0
        info.totalFailed() >= 0
        
        cleanup:
        batcher?.close()
    }
}

