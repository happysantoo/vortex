package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureResultSpec extends Specification {

    def "should create accept result"() {
        when:
        BackpressureResult<String> result = BackpressureResult.accept("item")
        
        then:
        result.action() == BackpressureAction.ACCEPT
        result.item() == "item"
        result.reason() == null
    }
    
    def "should create reject result with exception"() {
        given:
        Exception reason = new RuntimeException("Test error")
        
        when:
        BackpressureResult<String> result = BackpressureResult.reject("item", reason)
        
        then:
        result.action() == BackpressureAction.REJECT
        result.item() == "item"
        result.reason() == reason
    }
    
    def "should throw exception when reject called with null reason"() {
        when:
        BackpressureResult.reject("item", null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should create drop result"() {
        when:
        BackpressureResult<String> result = BackpressureResult.drop("item")
        
        then:
        result.action() == BackpressureAction.DROP
        result.item() == "item"
        result.reason() == null
    }
    
    def "should throw exception when reject result has null reason"() {
        when:
        new BackpressureResult<>(BackpressureAction.REJECT, "item", null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception when non-reject result has non-null reason"() {
        given:
        Exception reason = new RuntimeException("Test")
        
        when:
        new BackpressureResult<>(BackpressureAction.ACCEPT, "item", reason)
        
        then:
        thrown(IllegalArgumentException)
    }
}

