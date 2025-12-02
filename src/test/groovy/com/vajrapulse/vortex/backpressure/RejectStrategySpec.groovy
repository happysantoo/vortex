package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class RejectStrategySpec extends Specification {

    def "should accept item when backpressure below threshold"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.5, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.ACCEPT
        result.item() == "item"
        result.reason() == null
    }
    
    def "should reject item when backpressure at threshold"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.7, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.REJECT
        result.item() == "item"
        result.reason() != null
        result.reason() instanceof BackpressureException
        result.reason().getMessage().contains("0.7")
        result.reason().getMessage().contains("Test Provider")
    }
    
    def "should reject item when backpressure above threshold"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.9, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.REJECT
        result.reason() != null
        result.reason().getBackpressureLevel() == 0.9
        result.reason().getThreshold() == 0.7
        result.reason().getSourceName() == "Test Provider"
    }
    
    def "should throw exception for threshold below 0.0"() {
        when:
        new RejectStrategy<String>(-0.1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for threshold above 1.0"() {
        when:
        new RejectStrategy<String>(1.1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should create exception with correct details when rejecting"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.8, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.REJECT
        result.reason() instanceof BackpressureException
        BackpressureException exception = result.reason() as BackpressureException
        exception.backpressureLevel == 0.8
        exception.threshold == 0.7
        exception.sourceName == "Test Provider"
        exception.message.contains("0.8")
        exception.message.contains("0.7")
        exception.message.contains("Test Provider")
    }
    
    def "should accept item exactly at threshold"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.699, provider  // Just below threshold
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.ACCEPT
    }
    
    def "should return threshold via getThreshold method"() {
        given:
        RejectStrategy<String> strategy = new RejectStrategy<>(0.7)
        
        expect:
        strategy.getThreshold() == 0.7
    }
    
    def "should return correct threshold for different values"() {
        when:
        RejectStrategy<String> strategy = new RejectStrategy<>(thresholdValue)
        
        then:
        strategy.getThreshold() == thresholdValue
        
        where:
        thresholdValue << [0.0, 0.5, 0.7, 1.0]
    }
}

