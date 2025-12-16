package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.ItemResult
import com.vajrapulse.vortex.results.SuccessEvent
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate

class MicroBatcherFactoryMethodsSpec extends Specification {

    def "should create high-throughput batcher using constructor with preset"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, BatcherConfig.highThroughputPreset(), registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 100
        batcher.getConfig().lingerTime == Duration.ofMillis(500)
        batcher.getConfig().maxQueueSize == 500
        
        cleanup:
        batcher?.close()
    }
    
    def "should create low-latency batcher using constructor with preset"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, BatcherConfig.lowLatencyPreset(), registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 5
        batcher.getConfig().lingerTime == Duration.ofMillis(10)
        batcher.getConfig().maxQueueSize == 20
        
        cleanup:
        batcher?.close()
    }
    
    def "should create balanced batcher using constructor with preset"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, BatcherConfig.balancedPreset(), registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 20
        batcher.getConfig().lingerTime == Duration.ofMillis(100)
        batcher.getConfig().maxQueueSize == 50
        
        cleanup:
        batcher?.close()
    }
    
    def "should create resilient batcher using constructor with preset"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        Predicate<Throwable> retryable = { it instanceof IOException }
        
        when:
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, BatcherConfig.resilientPreset(retryable), registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 10
        batcher.getConfig().lingerTime == Duration.ofMillis(100)
        batcher.getConfig().maxRetries == 3
        batcher.getConfig().retryDelay == Duration.ofMillis(100)
        batcher.getConfig().retryableErrorPredicate == retryable
        batcher.getConfig().maxQueueSize == 30
        
        cleanup:
        batcher?.close()
    }
    
    def "should work with constructor and preset"() {
        given:
        def batchResults = []
        Backend<String> backend = { batch ->
            def result = new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
            batchResults.add(result)
            result
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, BatcherConfig.balancedPreset(), registry)
        def submitResult = batcher.submit("test")
        Thread.sleep(200) // Wait for batch processing
        
        then:
        submitResult instanceof ItemResult.Success
        batchResults.size() >= 1
        batchResults[0].isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
}

