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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
    
    def "should handle overflow strategy with lifecycle callbacks"() {
        given:
        Backend<String> backend = { batch ->
            def successes = batch.collect { new SuccessEvent<>(it) }
            new BatchResult<>(successes, List.of())
        }
        
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(50))
            .maxQueueSize(5)
            .build()
        
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(100)
        
        def queueDepthRef = new AtomicInteger(4)  // Start with high queue depth to trigger backpressure (4/5 = 0.8)
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            { queueDepthRef.get() },
            config.getMaxQueueSize()
        )
        
        def pauseCalled = new AtomicBoolean(false)
        def resumeCalled = new AtomicBoolean(false)
        def submittedItems = Collections.synchronizedList(new ArrayList<String>())
        
        // Create strategy with a simple submit function that just tracks items
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.6,  // Lower threshold to trigger earlier (4/5 = 0.8 > 0.6)
            overflow,
            provider,
            { item -> 
                submittedItems.add(item)
                // Return a completed future to avoid blocking
                CompletableFuture.completedFuture(new BatchResult<>(List.of(new SuccessEvent<>(item)), List.of()))
            },
            { pauseCalled.set(true) },
            { resumeCalled.set(true) }
        )
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
        // Wait for monitoring to detect backpressure (monitor runs every 100ms, threshold is 0.6)
        // Queue depth 4/5 = 0.8 > 0.6, so backpressure should be active
        when:
        Thread.sleep(250)  // Wait for at least 2 monitoring cycles
        
        then:
        // Pause should be called when backpressure enters
        pauseCalled.get()
        
        // Now submit items - they should go to overflow because backpressure is active
        when:
        batcher.submit("item1")
        batcher.submit("item2")
        
        then:
        // Items should be in overflow (backpressure level 0.8 >= threshold 0.6)
        overflow.size() == 2
        
        // Now reduce queue depth to resolve backpressure
        when:
        queueDepthRef.set(2)  // 2/5 = 0.4 < 0.6, backpressure should resolve
        Thread.sleep(250)  // Wait for monitoring to detect resolution
        
        then:
        // Resume should be called when backpressure resolves
        resumeCalled.get()
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, composite, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<String> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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
        
        MicroBatcher<Integer> batcher = MicroBatcher.withBackpressure(
            backend, config, provider, strategy
        )
        
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

