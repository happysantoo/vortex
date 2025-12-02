package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureExceptionSpec extends Specification {

    def "should create exception with message and backpressure details"() {
        when:
        BackpressureException exception = new BackpressureException(
            "Backpressure too high", 0.8, 0.7, "Test Provider"
        )
        
        then:
        exception.message == "Backpressure too high"
        exception.getBackpressureLevel() == 0.8
        exception.getThreshold() == 0.7
        exception.getSourceName() == "Test Provider"
    }
    
    def "should create exception with cause"() {
        given:
        Throwable cause = new RuntimeException("Root cause")
        
        when:
        BackpressureException exception = new BackpressureException(
            "Backpressure error", cause, 0.9, 0.7, "Test Provider"
        )
        
        then:
        exception.message == "Backpressure error"
        exception.cause == cause
        exception.getBackpressureLevel() == 0.9
        exception.getThreshold() == 0.7
        exception.getSourceName() == "Test Provider"
    }
    
    def "should handle boundary backpressure levels"() {
        when:
        BackpressureException exception0 = new BackpressureException(
            "Low", 0.0, 0.0, "Provider"
        )
        BackpressureException exception1 = new BackpressureException(
            "High", 1.0, 1.0, "Provider"
        )
        
        then:
        exception0.getBackpressureLevel() == 0.0
        exception0.getThreshold() == 0.0
        exception1.getBackpressureLevel() == 1.0
        exception1.getThreshold() == 1.0
    }
    
    def "should handle null source name"() {
        when:
        BackpressureException exception = new BackpressureException(
            "Error", 0.5, 0.4, null
        )
        
        then:
        exception.getSourceName() == null
    }
}

