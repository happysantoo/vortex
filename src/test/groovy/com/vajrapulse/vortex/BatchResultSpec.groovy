package com.vajrapulse.vortex

import spock.lang.Specification

class BatchResultSpec extends Specification {

    def "should create batch result with successes and failures"() {
        given:
        def successes = [
            new SuccessEvent<>("item1"),
            new SuccessEvent<>("item2")
        ]
        def failures = [
            new FailureEvent<>("item3", new RuntimeException("error"))
        ]

        when:
        def result = new BatchResult<>(successes, failures)

        then:
        result.successes.size() == 2
        result.failures.size() == 1
        result.getTotalCount() == 3
        !result.isAllSuccess()
    }

    def "should create batch result with only successes"() {
        given:
        def successes = [
            new SuccessEvent<>("item1"),
            new SuccessEvent<>("item2")
        ]

        when:
        def result = new BatchResult<>(successes, null)

        then:
        result.successes.size() == 2
        result.failures.isEmpty()
        result.getTotalCount() == 2
        result.isAllSuccess()
    }

    def "should create batch result with only failures"() {
        given:
        def failures = [
            new FailureEvent<>("item1", new RuntimeException("error1")),
            new FailureEvent<>("item2", new RuntimeException("error2"))
        ]

        when:
        def result = new BatchResult<>(null, failures)

        then:
        result.successes.isEmpty()
        result.failures.size() == 2
        result.getTotalCount() == 2
        !result.isAllSuccess()
    }

    def "should create empty batch result"() {
        when:
        def result = new BatchResult<>(null, null)

        then:
        result.successes.isEmpty()
        result.failures.isEmpty()
        result.getTotalCount() == 0
        result.isAllSuccess()
    }

    def "should return unmodifiable lists"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        when:
        result.successes.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        result.failures.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "should handle large batch results"() {
        given:
        def successes = (1..1000).collect { new SuccessEvent<>("item$it") }
        def failures = (1..500).collect { new FailureEvent<>("fail$it", new RuntimeException("error$it")) }

        when:
        def result = new BatchResult<>(successes, failures)

        then:
        result.successes.size() == 1000
        result.failures.size() == 500
        result.getTotalCount() == 1500
    }
}

