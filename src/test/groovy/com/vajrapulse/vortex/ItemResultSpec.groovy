package com.vajrapulse.vortex

import spock.lang.Specification

class ItemResultSpec extends Specification {

    def "should create success result"() {
        when:
        def result = ItemResult.success("item1")

        then:
        result instanceof ItemResult.Success
        result.item == "item1"
        result.getItem() == "item1"
    }

    def "should create failure result"() {
        given:
        def error = new RuntimeException("test error")

        when:
        def result = ItemResult.failure("item1", error)

        then:
        result instanceof ItemResult.Failure
        result.item == "item1"
        result.error == error
        result.getItem() == "item1"
    }

    def "should create success from SuccessEvent"() {
        given:
        def event = new com.vajrapulse.vortex.results.SuccessEvent<>("item1")

        when:
        def result = ItemResult.success(event)

        then:
        result instanceof ItemResult.Success
        result.item == "item1"
    }

    def "should create failure from FailureEvent"() {
        given:
        def error = new RuntimeException("test error")
        def event = new com.vajrapulse.vortex.results.FailureEvent<>("item1", error)

        when:
        def result = ItemResult.failure(event)

        then:
        result instanceof ItemResult.Failure
        result.item == "item1"
        result.error == error
    }

    def "should throw exception when creating failure with null error"() {
        when:
        ItemResult.failure("item1", null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should support pattern matching with switch expressions"() {
        given:
        def successResult = ItemResult.success("item1")
        def failureResult = ItemResult.failure("item2", new RuntimeException("error"))

        when:
        def successType = switch (successResult) {
            case ItemResult.Success -> "success"
            case ItemResult.Failure -> "failure"
        }

        def failureType = switch (failureResult) {
            case ItemResult.Success -> "success"
            case ItemResult.Failure -> "failure"
        }

        then:
        successType == "success"
        failureType == "failure"
    }

    def "should handle null items in success"() {
        when:
        def result = ItemResult.success(null)

        then:
        result instanceof ItemResult.Success
        result.item == null
    }

    def "should handle null items in failure"() {
        given:
        def error = new RuntimeException("error")

        when:
        def result = ItemResult.failure(null, error)

        then:
        result instanceof ItemResult.Failure
        result.item == null
        result.error == error
    }

    def "should throw exception when Failure record created with null error"() {
        when:
        new ItemResult.Failure<>("item1", null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create Failure record with valid error"() {
        given:
        def error = new RuntimeException("test error")

        when:
        def failure = new ItemResult.Failure<>("item1", error)

        then:
        failure.item == "item1"
        failure.error == error
        failure.getItem() == "item1"
    }

    def "should create Success record"() {
        when:
        def success = new ItemResult.Success<>("item1")

        then:
        success.item == "item1"
        success.getItem() == "item1"
    }

    def "should create Success record with null item"() {
        when:
        def success = new ItemResult.Success<>(null)

        then:
        success.item == null
        success.getItem() == null
    }
}

