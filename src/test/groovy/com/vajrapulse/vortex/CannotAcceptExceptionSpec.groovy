package com.vajrapulse.vortex

import spock.lang.Specification

class CannotAcceptExceptionSpec extends Specification {

    def "should create queue full exception"() {
        when:
        def exception = CannotAcceptException.queueFull(10, 20)

        then:
        exception instanceof CannotAcceptException
        exception.currentLevel == 10
        exception.maxLevel == 20
        exception.sourceName == "Vortex Queue Depth"
        exception.message.contains("Queue full")
        exception.message.contains("10/20")
    }

    def "should create concurrent limit exception"() {
        when:
        def exception = CannotAcceptException.concurrentLimitReached(8, 10)

        then:
        exception instanceof CannotAcceptException
        exception.currentLevel == 8
        exception.maxLevel == 10
        exception.sourceName == "Concurrent Batches"
        exception.message.contains("too many concurrent batches")
        exception.message.contains("active: 8")
        exception.message.contains("limit: 10")
    }

    def "should create exception with custom message"() {
        when:
        def exception = new CannotAcceptException("Custom message", 5, 10, "Custom Source")

        then:
        exception.message == "Custom message"
        exception.currentLevel == 5
        exception.maxLevel == 10
        exception.sourceName == "Custom Source"
    }

    def "should create exception with cause"() {
        given:
        def cause = new RuntimeException("Root cause")

        when:
        def exception = new CannotAcceptException("Custom message", cause, 5, 10, "Custom Source")

        then:
        exception.message == "Custom message"
        exception.cause == cause
        exception.currentLevel == 5
        exception.maxLevel == 10
        exception.sourceName == "Custom Source"
    }

    def "should get current level"() {
        when:
        def exception = CannotAcceptException.queueFull(15, 20)

        then:
        exception.getCurrentLevel() == 15
    }

    def "should get max level"() {
        when:
        def exception = CannotAcceptException.queueFull(15, 20)

        then:
        exception.getMaxLevel() == 20
    }

    def "should get source name"() {
        when:
        def exception = CannotAcceptException.queueFull(15, 20)

        then:
        exception.getSourceName() == "Vortex Queue Depth"
    }
}

