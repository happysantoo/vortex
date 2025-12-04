package com.vajrapulse.vortex.tracing

import com.vajrapulse.vortex.BatchResult
import com.vajrapulse.vortex.FailureEvent
import com.vajrapulse.vortex.SuccessEvent
import spock.lang.Specification

class OpenTelemetryTracingHookSpec extends Specification {

    def "should create hook without OpenTelemetry in classpath"() {
        when:
        def hook = new OpenTelemetryTracingHook()

        then:
        hook != null
        // Hook should be created successfully even without OpenTelemetry
        // It will be a no-op if OpenTelemetry is not available
    }

    def "should handle onSubmit when OpenTelemetry not available"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onSubmit("item-1")

        then:
        // Should not throw exception - no-op when OpenTelemetry not available
        noExceptionThrown()
    }

    def "should handle onBatchDispatchStart when OpenTelemetry not available"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onBatchDispatchStart(["item-1", "item-2"])

        then:
        // Should not throw exception - no-op when OpenTelemetry not available
        noExceptionThrown()
    }

    def "should handle onBatchDispatchSuccess when OpenTelemetry not available"() {
        given:
        def hook = new OpenTelemetryTracingHook()
        def batchResult = new BatchResult<>(
            [new SuccessEvent<>("item-1")],
            [new FailureEvent<>("item-2", new RuntimeException("error"))]
        )

        when:
        hook.onBatchDispatchSuccess(["item-1", "item-2"], batchResult)

        then:
        // Should not throw exception - no-op when OpenTelemetry not available
        noExceptionThrown()
    }

    def "should handle onBatchDispatchFailure when OpenTelemetry not available"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onBatchDispatchFailure(["item-1"], new RuntimeException("error"))

        then:
        // Should not throw exception - no-op when OpenTelemetry not available
        noExceptionThrown()
    }

    def "should handle onRetry when OpenTelemetry not available"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onRetry("item-1", new RuntimeException("retry error"))

        then:
        // Should not throw exception - no-op when OpenTelemetry not available
        noExceptionThrown()
    }

    def "should handle onRetry with null item"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onRetry(null, new RuntimeException("error"))

        then:
        // Should not throw exception - gracefully handles null
        noExceptionThrown()
    }

    def "should handle onRetry with null cause"() {
        given:
        def hook = new OpenTelemetryTracingHook()

        when:
        hook.onRetry("item-1", null)

        then:
        // Should not throw exception - gracefully handles null
        noExceptionThrown()
    }
}

