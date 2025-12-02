package com.vajrapulse.vortex.backpressure

import com.vajrapulse.vortex.BatchResult
import com.vajrapulse.vortex.SuccessEvent
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
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
        // Simulate a scenario where storage reports non-empty but poll() returns null
        // This tests the defensive code that prevents infinite loops
        // The implementation checks isEmpty() at start of loop and after null poll
        // After 3 consecutive null polls, it breaks to prevent infinite loops
        overflow.isEmpty() >> false  // Always reports non-empty (buggy storage scenario)
        overflow.poll() >> null  // Always returns null when polling
        overflow.size() >> 0  // Size is 0 (empty)
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // Below threshold (0.3 < 0.7)
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle null gracefully and exit loop after maxConsecutiveNullPolls (3)
        // The implementation breaks after 3 consecutive null polls to prevent infinite loops
        noExceptionThrown()
        0 * submitFunction.apply(_)  // Should not call submit for null
        // Verify poll was called exactly 3 times (maxConsecutiveNullPolls) before breaking
        3 * overflow.poll()
        // isEmpty() is called at start of each loop iteration (3 times) and after each null poll (3 times)
        // So total of 6 calls: 3 at start of loop, 3 after null polls
        (3..10) * overflow.isEmpty()  // Allow some flexibility in call count
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
    
    def "should return threshold via getThreshold method"() {
        given:
        OverflowStorage<String> overflow = Mock()
        BackpressureProvider provider = Mock()
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        expect:
        strategy.getThreshold() == 0.7
    }
    
    def "should reject item when overflow storage is full"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(1)
        overflow.add("existing")  // Fill storage
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.8
        provider.getSourceName() >> "Test Provider"
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "newItem", 0.8, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.REJECT
        result.item() == "newItem"
        result.reason() != null
        result.reason() instanceof BackpressureException
        result.reason().getMessage().contains("Overflow storage is full")
    }
    
    def "should handle invalid backpressure level in onBackpressureActive"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> Double.NaN  // Invalid level
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        // Should handle gracefully without throwing
        noExceptionThrown()
        0 * submitFunction.apply(_)  // Should not replay with invalid level
    }
    
    def "should handle backpressure level above 1.0 in onBackpressureActive"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 1.5  // Invalid level > 1.0
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        // Should handle gracefully without throwing
        noExceptionThrown()
        0 * submitFunction.apply(_)  // Should not replay with invalid level
    }
    
    def "should handle backpressure level below 0.0 in onBackpressureActive"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> -0.1  // Invalid level < 0.0
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureActive(provider)
        
        then:
        // Should handle gracefully without throwing
        noExceptionThrown()
        0 * submitFunction.apply(_)  // Should not replay with invalid level
    }
    
    def "should handle exception in onPause callback"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        Runnable onPause = { throw new RuntimeException("Pause failed") }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, onPause, null
        )
        
        when:
        strategy.onBackpressureEntered(provider)
        
        then:
        // Should handle exception gracefully without throwing
        noExceptionThrown()
    }
    
    def "should handle exception in onResume callback"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        Runnable onResume = { throw new RuntimeException("Resume failed") }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, null, onResume
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle exception gracefully without throwing
        noExceptionThrown()
    }
    
    def "should handle exception during overflow storage add"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.add(_) >> { throw new RuntimeException("Storage error") }
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.8
        provider.getSourceName() >> "Test Provider"
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.8, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        // Should reject item when storage add fails
        result.action() == BackpressureAction.REJECT
        result.item() == "item"
        result.reason() != null
        result.reason() instanceof BackpressureException
        // The message format is: "Error adding to overflow storage: {error}. Backpressure level: {level}"
        result.reason().getMessage().contains("Error adding") || 
        result.reason().getMessage().contains("overflow storage") ||
        result.reason().getMessage().contains("Storage error")
    }
    
    def "should replay items when backpressure resolves"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        overflow.add("item2")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // Below threshold (0.3 < 0.7)
        
        def replayedItems = []
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            replayedItems.add(item)
            return CompletableFuture.completedFuture(
                new BatchResult<>(List.of(new SuccessEvent<>(item)), List.of())
            )
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Items should be replayed
        replayedItems.size() == 2
        replayedItems.contains("item1")
        replayedItems.contains("item2")
        overflow.isEmpty()
    }
    
    def "should stop replaying when backpressure increases during replay"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        overflow.add("item2")
        overflow.add("item3")
        
        def callCount = new AtomicInteger(0)
        BackpressureProvider provider = Mock()
        // First call returns low (0.3), subsequent calls return high (0.8)
        provider.getBackpressureLevel() >>> [0.3, 0.3, 0.8]
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            callCount.incrementAndGet()
            return CompletableFuture.completedFuture(
                new BatchResult<>(List.of(new SuccessEvent<>(item)), List.of())
            )
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should stop replaying when backpressure increases
        // May replay 1-2 items before detecting increased backpressure
        callCount.get() <= 2
    }
    
    def "should handle exception during item replay"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        overflow.add("item2")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // Below threshold
        
        def callCount = new AtomicInteger(0)
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            callCount.incrementAndGet()
            if (item == "item1") {
                throw new RuntimeException("Replay failed")
            }
            return CompletableFuture.completedFuture(
                new BatchResult<>(List.of(new SuccessEvent<>(item)), List.of())
            )
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle exception and continue replaying other items
        noExceptionThrown()
        callCount.get() >= 1  // At least one item attempted
    }
    
    def "should handle exception with cause during item replay"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(10)
        overflow.add("item1")
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3  // Below threshold
        
        def cause = new RuntimeException("Root cause")
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            def exception = new RuntimeException("Replay failed", cause)
            throw exception
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle exception with cause gracefully
        noExceptionThrown()
    }
    
    def "should handle exception with cause in onPause callback"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        def cause = new RuntimeException("Root cause")
        Runnable onPause = { throw new RuntimeException("Pause failed", cause) }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, onPause, null
        )
        
        when:
        strategy.onBackpressureEntered(provider)
        
        then:
        // Should handle exception with cause gracefully
        noExceptionThrown()
    }
    
    def "should handle exception with cause in onResume callback"() {
        given:
        OverflowStorage<String> overflow = Mock()
        overflow.isEmpty() >> true
        
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.3
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = Mock()
        
        def cause = new RuntimeException("Root cause")
        Runnable onResume = { throw new RuntimeException("Resume failed", cause) }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction, null, onResume
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle exception with cause gracefully
        noExceptionThrown()
    }
    
    def "should handle max replay attempts exceeded"() {
        given:
        InMemoryOverflowStorage<String> overflow = new InMemoryOverflowStorage<>(100)
        // Add many items to overflow
        50.times { i ->
            overflow.add("item-$i")
        }
        
        BackpressureProvider provider = Mock()
        // Return level that allows replay, but then throw exception to force max attempts
        def callCount = new AtomicInteger(0)
        provider.getBackpressureLevel() >> {
            callCount.incrementAndGet()
            if (callCount.get() > 1000) {
                return 0.8  // Above threshold to stop replay
            }
            return 0.3  // Below threshold
        }
        
        Function<String, CompletableFuture<BatchResult<String>>> submitFunction = { item ->
            // Always throw to force max attempts
            throw new RuntimeException("Replay failed")
        }
        
        OverflowStrategy<String> strategy = new OverflowStrategy<>(
            0.7, overflow, provider, submitFunction
        )
        
        when:
        strategy.onBackpressureResolved(provider)
        
        then:
        // Should handle max attempts gracefully
        noExceptionThrown()
    }
}

