package com.vajrapulse.vortex

import com.vajrapulse.vortex.backpressure.*
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MicroBatcherBackpressureSpec extends Specification {

    def "should accept items when backpressure is low"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // Low backpressure
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        result.isAllSuccess()
        result.successes.size() == 1
        
        cleanup:
        batcher?.close()
    }
    
    def "should drop items when backpressure is high with DropStrategy"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.8  // High backpressure
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Drop strategy returns success but item is not actually processed
        result.isAllSuccess()
        result.successes.size() == 1
        // Backend should not be called
        batcher.getMeterRegistry().counter("vortex.backpressure.dropped").count() == 1
        
        cleanup:
        batcher?.close()
    }
    
    def "should reject items when backpressure is high with RejectStrategy"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.8  // High backpressure
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        future.get(1, TimeUnit.SECONDS)
        
        then:
        def exception = thrown(Exception)
        exception.cause instanceof BackpressureException
        batcher.getMeterRegistry().counter("vortex.backpressure.rejected").count() == 1
        
        cleanup:
        batcher?.close()
    }
    
    def "should use backpressure from config"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle invalid backpressure level gracefully"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .debugMode(true)
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> Double.NaN  // Invalid
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should proceed normally (fail-safe)
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle strategy exception gracefully"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .debugMode(true)
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new BackpressureStrategy<String>() {
            @Override
            BackpressureResult<String> handle(BackpressureContext<String> context) {
                throw new RuntimeException("Strategy error")
            }
        }
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should proceed normally (fail-safe)
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should work without backpressure (backward compatible)"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should use composite backpressure provider"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .maxQueueSize(10)
            .build()
        
        QueueDepthBackpressureProvider queueProvider = new QueueDepthBackpressureProvider(
            () -> 5, 10
        )
        
        BackpressureProvider customProvider = Mock()
        customProvider.getBackpressureLevel() >> 0.8
        customProvider.getSourceName() >> "Custom Provider"
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            queueProvider, customProvider
        )
        
        BackpressureStrategy<String> strategy = new RejectStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(composite)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should reject because composite returns max (0.8) which is > 0.7
        def exception = thrown(Exception)
        exception.cause instanceof BackpressureException
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle backpressure with null item"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit(null)
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle backpressure provider returning NaN"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .debugMode(true)
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> Double.NaN
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should proceed normally (fail-safe, NaN treated as 0.0)
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle backpressure provider returning negative value"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .debugMode(true)
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> -0.1
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should proceed normally (fail-safe, negative treated as 0.0)
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle backpressure provider returning value above 1.0"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .debugMode(true)
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 1.5
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<String>> future = batcher.submit("item1")
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        // Should proceed normally (fail-safe, >1.0 treated as 0.0)
        result.isAllSuccess()
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle multiple backpressure checks in sequence"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >>> [0.3, 0.8, 0.2, 0.9]  // Varying levels
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<String> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<String> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        def futures = []
        4.times { index ->
            futures << batcher.submit("item-${index}")
        }
        
        def results = futures.collect { it.get(1, TimeUnit.SECONDS) }
        
        then:
        // First and third should be accepted (0.3, 0.2 < 0.7)
        // Second and fourth should be dropped (0.8, 0.9 >= 0.7)
        results.size() == 4
        batcher.getMeterRegistry().counter("vortex.backpressure.dropped").count() == 2
        
        cleanup:
        batcher?.close()
    }
    
    def "should handle backpressure with different item types"() {
        given:
        Backend<Integer> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        provider.getSourceName() >> "Test Provider"
        
        BackpressureStrategy<Integer> strategy = new DropStrategy<>(0.7)
        
        def configWithBackpressure = BatcherConfig.builder()
            .batchSize(config.getBatchSize())
            .lingerTime(config.getLingerTime())
            .backpressureMonitorInterval(config.getBackpressureMonitorInterval())
            .maxQueueSize(config.getMaxQueueSize())
            .debugMode(config.isDebugMode())
            .backpressureProvider(provider)
            .backpressureStrategy(strategy)
            .build()
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry()
        MicroBatcher<Integer> batcher = new MicroBatcher<>(backend, configWithBackpressure, registry)
        
        when:
        CompletableFuture<BatchResult<Integer>> future = batcher.submit(42)
        def result = future.get(1, TimeUnit.SECONDS)
        
        then:
        result.isAllSuccess()
        result.successes.size() == 1
        result.successes[0].getData() == 42
        
        cleanup:
        batcher?.close()
    }
}

