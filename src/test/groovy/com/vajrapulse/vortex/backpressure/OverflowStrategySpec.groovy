package com.vajrapulse.vortex.backpressure

import com.vajrapulse.vortex.BatchResult
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.function.Function

class OverflowStrategySpec extends Specification {

    def "should accept item when backpressure below threshold"() {
        given:
        OverflowStorage<String> overflow = Mock()
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        provider.getSourceName() >> "Test Provider"
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.5, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.ACCEPT
        result.item() == "item"
        0 * overflow.add(_)
    }
    
    def "should drop and store item when backpressure at threshold"() {
        given:
        OverflowStorage<String> overflow = Mock()
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.7
        provider.getSourceName() >> "Test Provider"
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.7, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.DROP
        result.item() == "item"
        1 * overflow.add("item")
    }
    
    def "should call onPause when backpressure enters"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        def pauseCalled = false
        Runnable onPause = { pauseCalled = true }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, onPause, null
        )
        
        when:
        strategy.onBackpressureEntered(provider)
        
        then:
        pauseCalled
    }
    
    def "should call onResume when backpressure resolves"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        def resumeCalled = false
        Runnable onResume = { resumeCalled = true }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, null, onResume
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        resumeCalled
    }
    
    def "should replay items when backpressure resolves"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        overflow.add("item2")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        def submittedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            submittedItems.add(item)
            return CompletableFuture.completedFuture(new BatchResult<>(List.of(), List.of()))
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        submittedItems.contains("item1")
        submittedItems.contains("item2")
        overflow.isEmpty()
    }
    
    def "should start gradual replay when backpressure reduces to 50% of threshold"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // 0.3 < 0.7 * 0.5 = 0.35
        
        def submittedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            submittedItems.add(item)
            return CompletableFuture.completedFuture(new BatchResult<>(List.of(), List.of()))
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        submittedItems.contains("item1")
        overflow.isEmpty()
    }
    
    def "should not replay when backpressure still high"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5  // 0.5 >= 0.7 * 0.5 = 0.35
        
        def submittedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            submittedItems.add(item)
            return CompletableFuture.completedFuture(new BatchResult<>(List.of(), List.of()))
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        submittedItems.isEmpty()
        !overflow.isEmpty()
    }
    
    def "should throw exception for invalid threshold"() {
        given:
        OverflowStorage<String> overflow = Mock()
        BackpressureProvider provider = Mock()
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        when:
        new OverflowStrategy<>(-0.1, overflow, provider, submitFunction)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for null overflow storage"() {
        given:
        BackpressureProvider provider = Mock()
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        when:
        new OverflowStrategy<>(0.7, null, provider, submitFunction)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for null provider"() {
        given:
        OverflowStorage<String> overflow = Mock()
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        when:
        new OverflowStrategy<>(0.7, overflow, null, submitFunction)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for null submit function"() {
        given:
        OverflowStorage<String> overflow = Mock()
        BackpressureProvider provider = Mock()
        
        when:
        new OverflowStrategy<>(0.7, overflow, provider, null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should handle onPause callback exception gracefully"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        def pauseExceptionThrown = false
        Runnable onPause = { 
            throw new RuntimeException("Pause error")
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, onPause, null
        )
        
        when:
        strategy.onBackpressureEntered(provider)
        
        then:
        // Should not throw, exception should be caught internally
        noExceptionThrown()
    }
    
    def "should handle onResume callback exception gracefully"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        Runnable onResume = { 
            throw new RuntimeException("Resume error")
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, null, onResume
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should not throw, exception should be caught internally
        noExceptionThrown()
    }
    
    def "should handle submit function exception during replay"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        overflow.add("item2")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        
        def submittedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            if (item == "item1") {
                throw new RuntimeException("Submit error")
            }
            submittedItems.add(item)
            return CompletableFuture.completedFuture(new BatchResult<>(List.of(), List.of()))
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should continue replaying despite error on first item
        submittedItems.contains("item2")
        overflow.isEmpty()  // All items should be attempted
    }
    
    def "should not replay when backpressure still high in onBackpressureActive"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.6  // 0.6 >= 0.7 * 0.5 = 0.35, so no replay
        
        def submittedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            submittedItems.add(item)
            return CompletableFuture.completedFuture(new BatchResult<>(List.of(), List.of()))
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        submittedItems.isEmpty()
        !overflow.isEmpty()
    }
    
    def "should handle null item in overflow storage"() {
        given:
        OverflowStorage<String> overflow = Mock()
        // First call returns false (not empty), subsequent calls return true (empty after null poll)
        overflow.isEmpty() >>> [false, true]
        overflow.poll() >> null  // Return null when polling
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle null gracefully and exit loop
        noExceptionThrown()
        0 * submitFunction.apply(_)  // Should not call submit for null
    }
    
    def "should work without pause/resume callbacks"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureEntered(provider)
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should work without callbacks
        noExceptionThrown()
    }
}

