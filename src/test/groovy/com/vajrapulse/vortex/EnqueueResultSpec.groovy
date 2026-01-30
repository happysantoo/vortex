package com.vajrapulse.vortex

import com.vajrapulse.vortex.internal.EnqueueResult
import spock.lang.Specification

class EnqueueResultSpec extends Specification {

    def "ACCEPTED constant has correct type and predicates"() {
        expect:
        EnqueueResult.ACCEPTED.type == EnqueueResult.Type.ACCEPTED
        EnqueueResult.ACCEPTED.isAccepted()
        !EnqueueResult.ACCEPTED.isRejected()
        !EnqueueResult.ACCEPTED.isInterrupted()
        EnqueueResult.ACCEPTED.queueSizeAtRejection == 0
        EnqueueResult.ACCEPTED.maxQueueSize == 0
    }

    def "rejected(REJECTED_THRESHOLD) returns result with correct type and values"() {
        when:
        def result = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)

        then:
        result.type == EnqueueResult.Type.REJECTED_THRESHOLD
        result.queueSizeAtRejection == 5
        result.maxQueueSize == 10
        !result.isAccepted()
        result.isRejected()
        !result.isInterrupted()
    }

    def "rejected(REJECTED_FULL) returns result with correct type and values"() {
        when:
        def result = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_FULL, 10, 10)

        then:
        result.type == EnqueueResult.Type.REJECTED_FULL
        result.queueSizeAtRejection == 10
        result.maxQueueSize == 10
        !result.isAccepted()
        result.isRejected()
        !result.isInterrupted()
    }

    def "rejected(REJECTED_CONCURRENT_BATCHES) returns result with correct type and values"() {
        when:
        def result = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_CONCURRENT_BATCHES, 0, 10)

        then:
        result.type == EnqueueResult.Type.REJECTED_CONCURRENT_BATCHES
        result.queueSizeAtRejection == 0
        result.maxQueueSize == 10
        !result.isAccepted()
        result.isRejected()
        !result.isInterrupted()
    }

    def "rejected throws IllegalArgumentException for invalid type"() {
        when:
        EnqueueResult.rejected(EnqueueResult.Type.ACCEPTED, 0, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "rejected throws IllegalArgumentException for INTERRUPTED type"() {
        when:
        EnqueueResult.rejected(EnqueueResult.Type.INTERRUPTED, 0, 0)

        then:
        thrown(IllegalArgumentException)
    }

    def "interrupted() returns result with INTERRUPTED type"() {
        when:
        def result = EnqueueResult.interrupted()

        then:
        result.type == EnqueueResult.Type.INTERRUPTED
        result.queueSizeAtRejection == 0
        result.maxQueueSize == 0
        !result.isAccepted()
        !result.isRejected()
        result.isInterrupted()
    }

    def "equals returns true for same instance"() {
        def result = EnqueueResult.ACCEPTED
        expect:
        result.equals(result)
    }

    def "equals returns true for same type and queue values"() {
        def a = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)
        def b = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)
        expect:
        a.equals(b)
        b.equals(a)
    }

    def "equals returns false for null"() {
        expect:
        !EnqueueResult.ACCEPTED.equals(null)
    }

    def "equals returns false for different class"() {
        expect:
        !EnqueueResult.ACCEPTED.equals("ACCEPTED")
    }

    def "equals returns false for different type"() {
        def accepted = EnqueueResult.ACCEPTED
        def rejected = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_FULL, 0, 0)
        expect:
        !accepted.equals(rejected)
        !rejected.equals(accepted)
    }

    def "equals returns false for different queue sizes"() {
        def a = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)
        def b = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 6, 10)
        expect:
        !a.equals(b)
    }

    def "hashCode is consistent with equals"() {
        def a = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)
        def b = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 5, 10)
        expect:
        a.hashCode() == b.hashCode()
    }

    def "hashCode differs for different values"() {
        def a = EnqueueResult.ACCEPTED
        def b = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_FULL, 1, 2)
        expect:
        a.hashCode() != b.hashCode()
    }

    def "toString for ACCEPTED"() {
        expect:
        EnqueueResult.ACCEPTED.toString() == "EnqueueResult{ACCEPTED}"
    }

    def "toString for INTERRUPTED"() {
        expect:
        EnqueueResult.interrupted().toString() == "EnqueueResult{INTERRUPTED}"
    }

    def "toString for rejection types includes queue size"() {
        when:
        def result = EnqueueResult.rejected(EnqueueResult.Type.REJECTED_THRESHOLD, 7, 20)

        then:
        result.toString() == "EnqueueResult{REJECTED_THRESHOLD, queueSize=7/20}"
    }

    def "Type enum has all expected values"() {
        expect:
        EnqueueResult.Type.values().length == 5
        EnqueueResult.Type.ACCEPTED != null
        EnqueueResult.Type.REJECTED_THRESHOLD != null
        EnqueueResult.Type.REJECTED_FULL != null
        EnqueueResult.Type.REJECTED_CONCURRENT_BATCHES != null
        EnqueueResult.Type.INTERRUPTED != null
    }
}
