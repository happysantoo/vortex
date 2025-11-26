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

    def "should find item result in successes"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("item1"), new SuccessEvent<>("item2")],
            []
        )

        when:
        def itemResult = result.findItemResult("item1")

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Success
        itemResult.get().item == "item1"
    }

    def "should find item result in failures"() {
        given:
        def error = new RuntimeException("error")
        def result = new BatchResult<>(
            [],
            [new FailureEvent<>("item1", error), new FailureEvent<>("item2", error)]
        )

        when:
        def itemResult = result.findItemResult("item1")

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Failure
        itemResult.get().item == "item1"
        (itemResult.get() as ItemResult.Failure).error == error
    }

    def "should return empty when item not found"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        when:
        def itemResult = result.findItemResult("item3")

        then:
        !itemResult.isPresent()
    }

    def "should find item result with custom comparator"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("ITEM1"), new SuccessEvent<>("ITEM2")],
            []
        )

        when:
        def itemResult = result.findItemResult("item1") { a, b -> 
            a != null && b != null && a.toString().equalsIgnoreCase(b.toString())
        }

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Success
    }

    def "should check complete success"() {
        given:
        def successResult = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            []
        )
        def mixedResult = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        expect:
        successResult.isCompleteSuccess()
        !mixedResult.isCompleteSuccess()
    }

    def "should check complete failure"() {
        given:
        def failureResult = new BatchResult<>(
            [],
            [new FailureEvent<>("item1", new RuntimeException("error"))]
        )
        def mixedResult = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        expect:
        failureResult.isCompleteFailure()
        !mixedResult.isCompleteFailure()
    }

    def "should calculate failure rate"() {
        given:
        def allSuccess = new BatchResult<>(
            [new SuccessEvent<>("item1"), new SuccessEvent<>("item2")],
            []
        )
        def allFailure = new BatchResult<>(
            [],
            [new FailureEvent<>("item1", new RuntimeException("error")), 
             new FailureEvent<>("item2", new RuntimeException("error"))]
        )
        def mixed = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )
        def empty = new BatchResult<>(null, null)

        expect:
        allSuccess.getFailureRate() == 0.0
        allFailure.getFailureRate() == 1.0
        mixed.getFailureRate() == 0.5
        empty.getFailureRate() == 0.0
    }

    def "should group failures by type"() {
        given:
        def runtimeError = new RuntimeException("runtime")
        def illegalError = new IllegalArgumentException("illegal")
        def result = new BatchResult<>(
            [],
            [
                new FailureEvent<>("item1", runtimeError),
                new FailureEvent<>("item2", runtimeError),
                new FailureEvent<>("item3", illegalError)
            ]
        )

        when:
        def failuresByType = result.getFailuresByType()

        then:
        failuresByType.size() == 2
        failuresByType[RuntimeException].size() == 2
        failuresByType[IllegalArgumentException].size() == 1
    }

    def "should handle null items in findItemResult"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>(null)],
            []
        )

        when:
        def itemResult = result.findItemResult(null)

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Success
        itemResult.get().item == null
    }
}

