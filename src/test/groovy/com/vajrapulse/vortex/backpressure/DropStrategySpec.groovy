package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class DropStrategySpec extends Specification {

    def "should accept item when backpressure below threshold"() {
        given:
        DropStrategy<String> strategy = new DropStrategy<>(0.7)
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
    }
    
    def "should drop item when backpressure at threshold"() {
        given:
        DropStrategy<String> strategy = new DropStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.7, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.DROP
        result.item() == "item"
    }
    
    def "should drop item when backpressure above threshold"() {
        given:
        DropStrategy<String> strategy = new DropStrategy<>(0.7)
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.8, provider
        )
        
        when:
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == BackpressureAction.DROP
        result.item() == "item"
    }
    
    def "should throw exception for threshold below 0.0"() {
        when:
        new DropStrategy<String>(-0.1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for threshold above 1.0"() {
        when:
        new DropStrategy<String>(1.1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should accept boundary threshold values"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test"
        
        when:
        DropStrategy<String> strategy0 = new DropStrategy<>(0.0)
        DropStrategy<String> strategy1 = new DropStrategy<>(1.0)
        
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.5, provider
        )
        
        then:
        strategy0.handle(context).action() == BackpressureAction.DROP  // 0.5 >= 0.0
        strategy1.handle(context).action() == BackpressureAction.ACCEPT  // 0.5 < 1.0
    }
    
    def "should handle item exactly at threshold"() {
        given:
        DropStrategy<String> strategy = new DropStrategy<>(0.7)
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
    
    def "should handle various threshold values"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test"
        
        when:
        DropStrategy<String> strategy = new DropStrategy<>(thresholdValue)
        BackpressureContext<String> context = new BackpressureContext<>("item", backpressureLevel, provider)
        BackpressureResult<String> result = strategy.handle(context)
        
        then:
        result.action() == expectedAction
        
        where:
        thresholdValue | backpressureLevel | expectedAction
        0.5            | 0.4                | BackpressureAction.ACCEPT
        0.5            | 0.5                | BackpressureAction.DROP
        0.5            | 0.6                | BackpressureAction.DROP
        0.8            | 0.7                | BackpressureAction.ACCEPT
        0.8            | 0.8                | BackpressureAction.DROP
        0.8            | 0.9                | BackpressureAction.DROP
    }
}

