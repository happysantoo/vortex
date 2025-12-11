package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import com.vajrapulse.vortex.results.SuccessEvent
import com.vajrapulse.vortex.results.FailureEvent
import com.vajrapulse.vortex.results.ItemResult
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

    def "should find null item in failures"() {
        given:
        def error = new RuntimeException("error")
        def result = new BatchResult<>(
            [],
            [new FailureEvent<>(null, error)]
        )

        when:
        def itemResult = result.findItemResult(null)

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Failure
        itemResult.get().item == null
    }

    def "should find item with custom comparator returning true"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("ITEM1")],
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

    def "should not find item with custom comparator returning false"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("ITEM1")],
            []
        )

        when:
        def itemResult = result.findItemResult("item1") { a, b -> false }

        then:
        !itemResult.isPresent()
    }

    def "should handle getFailureRate with empty batch"() {
        given:
        def empty = new BatchResult<>(null, null)

        expect:
        empty.getFailureRate() == 0.0
    }

    def "should calculate failure rate correctly for various scenarios"() {
        given:
        def oneSuccess = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            []
        )
        def oneFailure = new BatchResult<>(
            [],
            [new FailureEvent<>("item1", new RuntimeException("error"))]
        )
        def twoSuccessOneFailure = new BatchResult<>(
            [new SuccessEvent<>("item1"), new SuccessEvent<>("item2")],
            [new FailureEvent<>("item3", new RuntimeException("error"))]
        )

        expect:
        oneSuccess.getFailureRate() == 0.0
        oneFailure.getFailureRate() == 1.0
        Math.abs(twoSuccessOneFailure.getFailureRate() - (1.0 / 3.0)) < 0.0001
    }

    def "should find item in successes before failures"() {
        given:
        def error = new RuntimeException("error")
        def result = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item1", error)]
        )

        when:
        def itemResult = result.findItemResult("item1")

        then:
        itemResult.isPresent()
        itemResult.get() instanceof ItemResult.Success
    }

    def "should handle findItemResult with null success data and non-null item"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>(null)],
            []
        )

        when:
        def itemResult = result.findItemResult("item1")

        then:
        !itemResult.isPresent()
    }

    def "should handle findItemResult with null failure data and non-null item"() {
        given:
        def result = new BatchResult<>(
            [],
            [new FailureEvent<>(null, new RuntimeException("error"))]
        )

        when:
        def itemResult = result.findItemResult("item1")

        then:
        !itemResult.isPresent()
    }

    def "should cover all getFailureRate branches"() {
        given:
        def empty = new BatchResult<>(null, null)
        def withItems = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        when:
        def emptyRate = empty.getFailureRate()
        def withItemsRate = withItems.getFailureRate()

        then:
        // Test the total == 0 branch
        emptyRate == 0.0
        // Test the total != 0 branch
        withItemsRate == 0.5
    }

    def "should cover all findItemResult branches with custom comparator"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>("item1"), new SuccessEvent<>(null)],
            [new FailureEvent<>("item2", new RuntimeException("error")), new FailureEvent<>(null, new RuntimeException("error2"))]
        )

        when:
        // Test: successData != null && comparator.test(successData, item) - TRUE path
        def found1 = result.findItemResult("item1") { a, b -> a != null && b != null && a == b }
        // Test: successData == null && item == null - TRUE path
        def found2 = result.findItemResult(null) { a, b -> a != null && b != null && a == b }
        // Test: failureData != null && comparator.test(failureData, item) - TRUE path
        def found3 = result.findItemResult("item2") { a, b -> a != null && b != null && a == b }
        // Test: failureData == null && item == null - TRUE path (when searching for null in failures)
        def found4 = result.findItemResult(null) { a, b -> a == b }
        // Test: successData != null && comparator.test(successData, item) - FALSE path (comparator returns false)
        def found5 = result.findItemResult("item1") { a, b -> false }
        // Test: failureData != null && comparator.test(failureData, item) - FALSE path (comparator returns false)
        def found6 = result.findItemResult("item2") { a, b -> false }

        then:
        found1.isPresent()
        found1.get() instanceof ItemResult.Success
        found2.isPresent() // Should find null in successes
        found3.isPresent()
        found3.get() instanceof ItemResult.Failure
        found4.isPresent() // Should find null in failures
        !found5.isPresent() // Comparator returns false
        !found6.isPresent() // Comparator returns false
    }

    def "should cover findItemResult when successData is null and item is not null"() {
        given:
        def result = new BatchResult<>(
            [new SuccessEvent<>(null)],
            []
        )

        when:
        def itemResult = result.findItemResult("non-null-item") { a, b -> a == b }

        then:
        !itemResult.isPresent()
    }

    def "should cover findItemResult when failureData is null and item is not null"() {
        given:
        def result = new BatchResult<>(
            [],
            [new FailureEvent<>(null, new RuntimeException("error"))]
        )

        when:
        def itemResult = result.findItemResult("non-null-item") { a, b -> a == b }

        then:
        !itemResult.isPresent()
    }

    def "should cover getFailureRate branch when total is zero"() {
        given:
        def empty = new BatchResult<>(null, null)

        when:
        def rate = empty.getFailureRate()

        then:
        // This should hit the total == 0 branch
        rate == 0.0
    }

    def "should cover getFailureRate branch when total is not zero"() {
        given:
        def withItems = new BatchResult<>(
            [new SuccessEvent<>("item1")],
            [new FailureEvent<>("item2", new RuntimeException("error"))]
        )

        when:
        def rate = withItems.getFailureRate()

        then:
        // This should hit the total != 0 branch
        rate == 0.5
    }

    def "should create BatchResult with null successes"() {
        when:
        def result = new BatchResult<>(null, [new FailureEvent<>("item1", new RuntimeException("error"))])

        then:
        result.successes.isEmpty()
        result.failures.size() == 1
    }

    def "should create BatchResult with null failures"() {
        when:
        def result = new BatchResult<>([new SuccessEvent<>("item1")], null)

        then:
        result.successes.size() == 1
        result.failures.isEmpty()
    }

    def "should create BatchResult with both null"() {
        when:
        def result = new BatchResult<>(null, null)

        then:
        result.successes.isEmpty()
        result.failures.isEmpty()
        result.getTotalCount() == 0
    }
}

