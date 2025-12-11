package com.vajrapulse.vortex

import spock.lang.Specification

class SuccessEventSpec extends Specification {

    def "should create success event with data"() {
        when:
        def event = new new SuccessEvent<>("test-data")

        then:
        event.data == "test-data"
    }

    def "should handle null data"() {
        when:
        def event = new new SuccessEvent<>(null)

        then:
        event.data == null
    }

    def "should handle complex data types"() {
        given:
        def complexData = [key: "value", count: 42]

        when:
        def event = new new SuccessEvent<>(complexData)

        then:
        event.data == complexData
    }
}

