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

    def "should create high-throughput batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = MicroBatcher.forHighThroughput(backend, registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 100
        batcher.getConfig().lingerTime == Duration.ofMillis(500)
        batcher.getConfig().maxQueueSize == 500
        
        cleanup:
        batcher?.close()
    }
    
    def "should create low-latency batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = MicroBatcher.forLowLatency(backend, registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 5
        batcher.getConfig().lingerTime == Duration.ofMillis(10)
        batcher.getConfig().maxQueueSize == 20
        
        cleanup:
        batcher?.close()
    }
    
    def "should create balanced batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = MicroBatcher.forBalanced(backend, registry)
        
        then:
        batcher != null
        batcher.getConfig().batchSize == 20
        batcher.getConfig().lingerTime == Duration.ofMillis(100)
        batcher.getConfig().maxQueueSize == 50
        
        cleanup:
        batcher?.close()
    }
    
    def "should create resilient batcher"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        Predicate<Throwable> retryable = { it instanceof IOException }
        
        when:
        MicroBatcher<String> batcher = MicroBatcher.forResilient(backend, registry, retryable)
        
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
    
    def "should work with factory methods"() {
        given:
        Backend<String> backend = { batch ->
            new BatchResult<>(batch.collect { new SuccessEvent<>(it) }, List.of())
        }
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        
        when:
        MicroBatcher<String> batcher = MicroBatcher.forBalanced(backend, registry)
        CompletableFuture<BatchResult<String>> future = batcher.submit("test")
        BatchResult<String> Thread.sleep(200); // Wait for batch processing)
        
        then:
        result != null
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
}

