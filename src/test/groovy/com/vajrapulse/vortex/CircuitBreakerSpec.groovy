package com.vajrapulse.vortex

import com.vajrapulse.vortex.internal.CircuitBreaker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.Collections

class CircuitBreakerSpec extends Specification {

    def "allowRequest returns true when closed"() {
        given:
        def cb = new CircuitBreaker(3, 1_000_000_000L, null)

        expect:
        cb.allowRequest()
        cb.state == CircuitBreaker.STATE_CLOSED
    }

    def "recordSuccess resets consecutive failures when closed"() {
        given:
        def cb = new CircuitBreaker(3, 1_000_000_000L, null)
        cb.recordFailure()
        cb.recordFailure()

        when:
        cb.recordSuccess()

        then:
        cb.consecutiveFailures == 0
        cb.state == CircuitBreaker.STATE_CLOSED
    }

    def "opens after threshold consecutive failures"() {
        given:
        def cb = new CircuitBreaker(3, 1_000_000_000L, null)

        when:
        3.times { cb.recordFailure() }

        then:
        cb.state == CircuitBreaker.STATE_OPEN
        !cb.allowRequest()
    }

    def "allowRequest returns false when open and duration not elapsed"() {
        given:
        def cb = new CircuitBreaker(2, 10_000_000_000L, null) // 10s open
        2.times { cb.recordFailure() }

        expect:
        cb.state == CircuitBreaker.STATE_OPEN
        !cb.allowRequest()
        !cb.allowRequest()
    }

    def "transitions to half-open after open duration and allows one probe"() {
        given:
        def cb = new CircuitBreaker(2, 0L, null) // 0ns open = immediately allow probe
        2.times { cb.recordFailure() }

        when:
        def allowed = cb.allowRequest()

        then:
        allowed
        cb.state == CircuitBreaker.STATE_HALF_OPEN
    }

    def "half-open allows only one probe request at a time"() {
        given:
        def cb = new CircuitBreaker(2, 0L, null)
        2.times { cb.recordFailure() }
        cb.allowRequest() // OPEN -> HALF_OPEN and reserves probe

        expect:
        cb.state == CircuitBreaker.STATE_HALF_OPEN
        // second attempt while probe is in-flight must be rejected
        !cb.allowRequest()

        when:
        cb.recordSuccess() // closes and clears probe flag

        then:
        cb.state == CircuitBreaker.STATE_CLOSED
    }

    def "half-open probe success closes circuit"() {
        given:
        def cb = new CircuitBreaker(2, 0L, null)
        2.times { cb.recordFailure() }
        cb.allowRequest() // now HALF_OPEN

        when:
        cb.recordSuccess()

        then:
        cb.state == CircuitBreaker.STATE_CLOSED
        cb.consecutiveFailures == 0
    }

    def "half-open probe failure reopens circuit"() {
        given:
        def cb = new CircuitBreaker(2, 0L, null)
        2.times { cb.recordFailure() }
        cb.allowRequest() // OPEN (0 elapsed) -> HALF_OPEN

        when:
        cb.recordFailure()

        then:
        cb.state == CircuitBreaker.STATE_OPEN
    }

    def "constructor rejects invalid failure threshold"() {
        when:
        new CircuitBreaker(0, 1_000_000_000L, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "constructor rejects invalid open duration"() {
        when:
        new CircuitBreaker(5, -1L, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "registers gauge and counter when MeterRegistry provided"() {
        given:
        def registry = new SimpleMeterRegistry()
        def cb = new CircuitBreaker(2, 1_000_000_000L, registry)

        when:
        2.times { cb.recordFailure() }

        then:
        registry.get("vortex.circuit.state").gauge().value() == CircuitBreaker.STATE_OPEN
        registry.get("vortex.circuit.open.events").counter().count() == 1
    }

    def "half-open probe is single under concurrency"() {
        given:
        def cb = new CircuitBreaker(1, 0L, null)
        cb.recordFailure() // CLOSED -> OPEN
        cb.allowRequest()  // OPEN -> HALF_OPEN and reserve probe

        def start = new CountDownLatch(1)
        def done = new CountDownLatch(8)
        def allowed = Collections.synchronizedList([] as List<Boolean>)
        def exec = Executors.newFixedThreadPool(8)

        when:
        8.times {
            exec.submit {
                start.await(2, TimeUnit.SECONDS)
                allowed.add(cb.allowRequest())
                done.countDown()
            }
        }
        start.countDown()
        done.await(2, TimeUnit.SECONDS)
        exec.shutdownNow()

        then:
        // Only one thread can acquire the probe; all others should be rejected.
        allowed.count { it } == 0
    }
}
