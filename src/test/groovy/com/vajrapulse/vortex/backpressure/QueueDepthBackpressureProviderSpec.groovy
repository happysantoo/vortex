package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class QueueDepthBackpressureProviderSpec extends Specification {

    def "should return 0.0 for empty queue"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 0, 100
        )
        
        when:
        double level = provider.getBackpressureLevel()
        
        then:
        level == 0.0
    }
    
    def "should return 1.0 for full queue"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 100, 100
        )
        
        when:
        double level = provider.getBackpressureLevel()
        
        then:
        level == 1.0
    }
    
    def "should return 1.0 for queue exceeding capacity"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 150, 100
        )
        
        when:
        double level = provider.getBackpressureLevel()
        
        then:
        level == 1.0
    }
    
    def "should return linear scaling for partial queue"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 50, 100
        )
        
        when:
        double level = provider.getBackpressureLevel()
        
        then:
        level == 0.5
    }
    
    def "should return correct level for various queue depths"() {
        given:
        def queueDepth = depth
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            { queueDepth }, 100
        )
        
        expect:
        provider.getBackpressureLevel() == expected
        
        where:
        depth | expected
        0     | 0.0
        25    | 0.25
        50    | 0.5
        75    | 0.75
        100   | 1.0
        150   | 1.0
    }
    
    def "should throw exception for zero max capacity"() {
        when:
        new QueueDepthBackpressureProvider(() -> 0, 0)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for negative max capacity"() {
        when:
        new QueueDepthBackpressureProvider(() -> 0, -1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for null supplier"() {
        when:
        new QueueDepthBackpressureProvider(null, 100)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should return correct source name"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 0, 100
        )
        
        when:
        String name = provider.getSourceName()
        
        then:
        name == "Vortex Queue Depth"
    }
    
    def "should return details with queue information"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 50, 100
        )
        
        when:
        Map<String, Object> details = provider.getDetails()
        
        then:
        details.containsKey("queueDepth")
        details.containsKey("maxCapacity")
        details.containsKey("utilization")
        details.get("queueDepth") == 50
        details.get("maxCapacity") == 100
        details.get("utilization").toString().contains("50.00")
    }
    
    def "should return details for empty queue"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 0, 100
        )
        
        when:
        Map<String, Object> details = provider.getDetails()
        
        then:
        details.get("queueDepth") == 0
        details.get("utilization").toString().contains("0.00")
    }
    
    def "should return details for full queue"() {
        given:
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            () -> 100, 100
        )
        
        when:
        Map<String, Object> details = provider.getDetails()
        
        then:
        details.get("queueDepth") == 100
        details.get("utilization").toString().contains("100.00")
    }
    
    def "should handle dynamic queue depth changes"() {
        given:
        def queueDepth = new AtomicInteger(0)
        QueueDepthBackpressureProvider provider = new QueueDepthBackpressureProvider(
            { queueDepth.get() }, 100
        )
        
        expect:
        queueDepth.set(depth)
        provider.getBackpressureLevel() == expected
        
        where:
        depth | expected
        0     | 0.0
        25    | 0.25
        50    | 0.5
        75    | 0.75
        100   | 1.0
        150   | 1.0
    }
}

