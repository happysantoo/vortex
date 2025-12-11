package com.vajrapulse.vortex

import spock.lang.Specification

class FailureEventSpec extends Specification {

    def "should create failure event with data and error"() {
        given:
        def error = new RuntimeException("test error")

        when:
        def event = new new FailureEvent<>("test-data", error)

        then:
        event.data == "test-data"
        event.error == error
        event.error.message == "test error"
    }

    def "should handle null data"() {
        given:
        def error = new IllegalArgumentException("invalid")

        when:
        def event = new new FailureEvent<>(null, error)

        then:
        event.data == null
        event.error == error
    }

    def "should handle null error"() {
        when:
        def event = new new FailureEvent<>("test-data", null)

        then:
        event.data == "test-data"
        event.error == null
    }

    def "should handle different exception types"() {
        given:
        def exceptions = [
            new RuntimeException("runtime"),
            new IllegalArgumentException("illegal"),
            new NullPointerException("null"),
            new IllegalStateException("state")
        ]

        when:
        def events = exceptions.collect { new new FailureEvent<>("data", it) }

        then:
        events.size() == 4
        events.every { it.data == "data" }
        events[0].error instanceof RuntimeException
        events[1].error instanceof IllegalArgumentException
    }
}

