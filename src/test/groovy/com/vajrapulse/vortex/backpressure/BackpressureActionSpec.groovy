package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureActionSpec extends Specification {

    def "should have all expected enum values"() {
        expect:
        BackpressureAction.values().length == 3
        BackpressureAction.ACCEPT != null
        BackpressureAction.REJECT != null
        BackpressureAction.DROP != null
    }
    
    def "should return correct enum value by name"() {
        expect:
        BackpressureAction.valueOf("ACCEPT") == BackpressureAction.ACCEPT
        BackpressureAction.valueOf("REJECT") == BackpressureAction.REJECT
        BackpressureAction.valueOf("DROP") == BackpressureAction.DROP
    }
    
    def "should throw exception for invalid enum name"() {
        when:
        BackpressureAction.valueOf("INVALID")
        
        then:
        thrown(IllegalArgumentException)
    }
}

