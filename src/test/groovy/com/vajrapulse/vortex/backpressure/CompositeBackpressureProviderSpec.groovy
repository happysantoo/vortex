package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class CompositeBackpressureProviderSpec extends Specification {

    def "should return maximum backpressure from all providers"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.3
        provider1.getSourceName() >> "Provider 1"
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 0.7
        provider2.getSourceName() >> "Provider 2"
        
        BackpressureProvider provider3 = Mock()
        provider3.getBackpressureLevel() >> 0.5
        provider3.getSourceName() >> "Provider 3"
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2, provider3
        )
        
        when:
        double level = composite.getBackpressureLevel()
        
        then:
        level == 0.7  // Maximum of 0.3, 0.7, 0.5
    }
    
    def "should return 0.0 when all providers return 0.0"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.0
        provider1.getSourceName() >> "Provider 1"
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 0.0
        provider2.getSourceName() >> "Provider 2"
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        double level = composite.getBackpressureLevel()
        
        then:
        level == 0.0
    }
    
    def "should return 1.0 when any provider returns 1.0"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.3
        provider1.getSourceName() >> "Provider 1"
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 1.0
        provider2.getSourceName() >> "Provider 2"
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        double level = composite.getBackpressureLevel()
        
        then:
        level == 1.0
    }
    
    def "should throw exception for empty providers"() {
        when:
        new CompositeBackpressureProvider()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for null providers array"() {
        when:
        new CompositeBackpressureProvider((BackpressureProvider[]) null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when provider is null"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getSourceName() >> "Provider 1"
        
        when:
        new CompositeBackpressureProvider(provider1, null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should return composite source name"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getSourceName() >> "Provider 1"
        provider1.getBackpressureLevel() >> 0.0
        
        BackpressureProvider provider2 = Mock()
        provider2.getSourceName() >> "Provider 2"
        provider2.getBackpressureLevel() >> 0.0
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        String name = composite.getSourceName()
        
        then:
        name == "Composite (2 sources)"
    }
    
    def "should return details with per-provider information"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.3
        provider1.getSourceName() >> "Provider 1"
        provider1.getDetails() >> Map.of("key1", "value1")
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 0.7
        provider2.getSourceName() >> "Provider 2"
        provider2.getDetails() >> Map.of("key2", "value2")
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        Map<String, Object> details = composite.getDetails()
        
        then:
        details.containsKey("maxBackpressure")
        details.get("maxBackpressure") == 0.7
        details.containsKey("providerCount")
        details.get("providerCount") == 2
        details.containsKey("provider0.name")
        details.containsKey("provider0.level")
        details.containsKey("provider1.name")
        details.containsKey("provider1.level")
        details.get("provider0.name") == "Provider 1"
        details.get("provider0.level") == 0.3
        details.get("provider1.name") == "Provider 2"
        details.get("provider1.level") == 0.7
    }
    
    def "should handle single provider"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        provider.getSourceName() >> "Single Provider"
        provider.getDetails() >> Map.of()
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(provider)
        
        when:
        double level = composite.getBackpressureLevel()
        String name = composite.getSourceName()
        Map<String, Object> details = composite.getDetails()
        
        then:
        level == 0.5
        name == "Composite (1 sources)"
        details.get("providerCount") == 1
        details.get("maxBackpressure") == 0.5
    }
    
    def "should handle multiple providers with same level"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.5
        provider1.getSourceName() >> "Provider 1"
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 0.5
        provider2.getSourceName() >> "Provider 2"
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        double level = composite.getBackpressureLevel()
        
        then:
        level == 0.5  // Max of 0.5 and 0.5
    }
    
    def "should handle providers with empty details"() {
        given:
        BackpressureProvider provider1 = Mock()
        provider1.getBackpressureLevel() >> 0.3
        provider1.getSourceName() >> "Provider 1"
        provider1.getDetails() >> Map.of()
        
        BackpressureProvider provider2 = Mock()
        provider2.getBackpressureLevel() >> 0.7
        provider2.getSourceName() >> "Provider 2"
        provider2.getDetails() >> Map.of()
        
        CompositeBackpressureProvider composite = new CompositeBackpressureProvider(
            provider1, provider2
        )
        
        when:
        Map<String, Object> details = composite.getDetails()
        
        then:
        details.containsKey("maxBackpressure")
        details.containsKey("providerCount")
        details.containsKey("provider0.name")
        details.containsKey("provider0.level")
        details.containsKey("provider1.name")
        details.containsKey("provider1.level")
    }
}

