package com.vajrapulse.vortex.internal;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple three-state circuit breaker for backend dispatch resilience.
 * <p>
 * States:
 * <ul>
 *   <li><strong>CLOSED</strong>: Normal operation; requests are allowed. Failures are counted.</li>
 *   <li><strong>OPEN</strong>: Backend is considered unhealthy; requests are rejected immediately.</li>
 *   <li><strong>HALF_OPEN</strong>: One probe request is allowed to test if the backend recovered.</li>
 * </ul>
 * <p>
 * Transitions:
 * <ul>
 *   <li>CLOSED → OPEN when consecutive failures reach the configured threshold.</li>
 *   <li>OPEN → HALF_OPEN after the configured open duration has elapsed (next allowRequest acts as probe).</li>
 *   <li>HALF_OPEN → CLOSED on first success after opening.</li>
 *   <li>HALF_OPEN → OPEN if the probe request fails.</li>
 * </ul>
 * <p>
 * Thread-safe: all state changes use atomic types and are safe for concurrent dispatch threads.
 */
public final class CircuitBreaker {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    /** Closed: normal operation. */
    public static final int STATE_CLOSED = 0;
    /** Open: rejecting requests. */
    public static final int STATE_OPEN = 1;
    /** Half-open: allowing one probe request. */
    public static final int STATE_HALF_OPEN = 2;

    private final int failureThreshold;
    private final long openDurationNanos;

    private final AtomicReference<State> state;
    private final AtomicInteger consecutiveFailures;
    private final AtomicLong openSinceNanos;

    private final Counter circuitOpenEvents;
    private final AtomicInteger stateGaugeValue;

    /**
     * Creates a circuit breaker.
     *
     * @param failureThreshold number of consecutive failures that open the circuit (must be positive)
     * @param openDurationNanos how long the circuit stays open before transitioning to half-open (nanos)
     * @param meterRegistry    optional registry for gauge and counter; may be null
     */
    public CircuitBreaker(int failureThreshold, long openDurationNanos, MeterRegistry meterRegistry) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (openDurationNanos < 0) {
            throw new IllegalArgumentException("openDurationNanos must be non-negative");
        }
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = openDurationNanos;
        this.state = new AtomicReference<>(State.CLOSED);
        this.consecutiveFailures = new AtomicInteger(0);
        this.openSinceNanos = new AtomicLong(0);
        this.stateGaugeValue = new AtomicInteger(STATE_CLOSED);

        if (meterRegistry != null) {
            this.circuitOpenEvents = Counter.builder("vortex.circuit.open.events")
                .description("Number of times the circuit transitioned to OPEN")
                .register(meterRegistry);
            Gauge.builder("vortex.circuit.state", stateGaugeValue, AtomicInteger::get)
                .description("Circuit state: 0=closed, 1=open, 2=half_open")
                .register(meterRegistry);
        } else {
            this.circuitOpenEvents = null;
        }
    }

    /**
     * Returns whether a request is allowed (circuit closed or half-open and allowing probe).
     * Call this before dispatching to the backend.
     * <p>
     * When the circuit is OPEN and the open duration has elapsed, this method transitions
     * to HALF_OPEN and returns true (one probe is allowed).
     *
     * @return true if the request may be dispatched, false if it should be rejected
     */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.HALF_OPEN) {
            return true;
        }
        // OPEN: check if we can transition to HALF_OPEN
        long openedAt = openSinceNanos.get();
        long elapsed = System.nanoTime() - openedAt;
        if (elapsed >= openDurationNanos && state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            stateGaugeValue.set(STATE_HALF_OPEN);
            logger.debug("Circuit breaker transitioning OPEN -> HALF_OPEN (probe allowed)");
            return true;
        }
        return false;
    }

    /**
     * Records a successful backend call. Resets failure count in CLOSED; transitions HALF_OPEN to CLOSED.
     */
    public void recordSuccess() {
        State current = state.get();
        if (current == State.CLOSED) {
            consecutiveFailures.set(0);
            return;
        }
        if (current == State.HALF_OPEN && state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            consecutiveFailures.set(0);
            stateGaugeValue.set(STATE_CLOSED);
            logger.debug("Circuit breaker transitioning HALF_OPEN -> CLOSED (probe succeeded)");
        }
    }

    /**
     * Records a failed backend call. Increments failure count; opens circuit when threshold is reached.
     */
    public void recordFailure() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openSinceNanos.set(System.nanoTime());
                stateGaugeValue.set(STATE_OPEN);
                if (circuitOpenEvents != null) {
                    circuitOpenEvents.increment();
                }
                logger.debug("Circuit breaker transitioning HALF_OPEN -> OPEN (probe failed)");
            }
            return;
        }
        if (current == State.CLOSED) {
            int failures = consecutiveFailures.incrementAndGet();
            if (failures >= failureThreshold && state.compareAndSet(State.CLOSED, State.OPEN)) {
                openSinceNanos.set(System.nanoTime());
                stateGaugeValue.set(STATE_OPEN);
                if (circuitOpenEvents != null) {
                    circuitOpenEvents.increment();
                }
                logger.debug("Circuit breaker transitioning CLOSED -> OPEN (failures={})", failures);
            }
        }
    }

    /**
     * Current state for testing and observability.
     *
     * @return STATE_CLOSED, STATE_OPEN, or STATE_HALF_OPEN
     */
    public int getState() {
        return stateGaugeValue.get();
    }

    /**
     * Current consecutive failure count (only meaningful when CLOSED).
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
