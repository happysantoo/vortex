package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureContextSpec extends Specification {

    def "should create context with valid values"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test Provider"
        
        when:
        BackpressureContext<String> context = new BackpressureContext<>(
            "item", 0.5, provider
        )
        
        then:
        context.item() == "item"
        context.backpressureLevel() == 0.5
        context.provider() == provider
    }
    
    def "should throw exception for null provider"() {
        when:
        new BackpressureContext<>("item", 0.5, null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for backpressure level below 0.0"() {
        given:
        BackpressureProvider provider = Mock()
        
        when:
        new BackpressureContext<>("item", -0.1, provider)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for backpressure level above 1.0"() {
        given:
        BackpressureProvider provider = Mock()
        
        when:
        new BackpressureContext<>("item", 1.1, provider)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should accept boundary values 0.0 and 1.0"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getSourceName() >> "Test"
        
        when:
        BackpressureContext<String> context0 = new BackpressureContext<>("item", 0.0, provider)
        BackpressureContext<String> context1 = new BackpressureContext<>("item", 1.0, provider)
        
        then:
        context0.backpressureLevel() == 0.0
        context1.backpressureLevel() == 1.0
    }
}

