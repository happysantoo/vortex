package com.vajrapulse.vortex.tracing

import com.vajrapulse.vortex.BatchResult
import com.vajrapulse.vortex.FailureEvent
import com.vajrapulse.vortex.SuccessEvent
import io.micrometer.tracing.Span
import io.micrometer.tracing.Tracer
import spock.lang.Specification

class MicrometerTracingHookSpec extends Specification {

    def tracer = Mock(Tracer)
    def span = Mock(Span)
    def spanInScope = Mock(Tracer.SpanInScope)

    def "should create hook with valid tracer"() {
        when:
        def hook = new MicrometerTracingHook(tracer)

        then:
        hook != null
    }

    def "should throw exception when tracer is null"() {
        when:
        new MicrometerTracingHook(null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should handle onSubmit"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onSubmit("item-1")

        then:
        1 * tracer.nextSpan() >> {
            def spanBuilder = Mock(Span.Builder)
            spanBuilder.name("vortex.submit") >> {
                def createdSpan = Mock(Span)
                createdSpan.tag("vortex.item.type", "String") >> createdSpan
                createdSpan.start()
                tracer.withSpan(createdSpan) >> spanInScope
                createdSpan
            }
            spanBuilder
        }
    }

    def "should handle onSubmit with null item"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onSubmit(null)

        then:
        0 * tracer.nextSpan()
    }

    def "should handle onBatchDispatchStart"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onBatchDispatchStart(["item-1", "item-2"])

        then:
        1 * tracer.nextSpan() >> {
            def spanBuilder = Mock(Span.Builder)
            spanBuilder.name("vortex.batch.dispatch") >> {
                def createdSpan = Mock(Span)
                createdSpan.tag("vortex.batch.size", "2") >> createdSpan
                createdSpan.start()
                tracer.withSpan(createdSpan) >> spanInScope
                createdSpan
            }
            spanBuilder
        }
    }

    def "should handle onBatchDispatchStart with null or empty list"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onBatchDispatchStart(null)
        hook.onBatchDispatchStart([])

        then:
        0 * tracer.nextSpan()
    }

    def "should handle onBatchDispatchSuccess"() {
        given:
        def hook = new MicrometerTracingHook(tracer)
        def batchResult = new BatchResult<>(
            [new SuccessEvent<>("item-1")],
            [new FailureEvent<>("item-2", new RuntimeException("error"))]
        )

        when:
        hook.onBatchDispatchSuccess(["item-1", "item-2"], batchResult)

        then:
        1 * tracer.currentSpan() >> span
        1 * span.tag("vortex.batch.success.count", "1")
        1 * span.tag("vortex.batch.failure.count", "1")
        1 * span.end()
    }

    def "should handle onBatchDispatchSuccess with no current span"() {
        given:
        def hook = new MicrometerTracingHook(tracer)
        def batchResult = new BatchResult<>(
            [new SuccessEvent<>("item-1")],
            []
        )

        when:
        hook.onBatchDispatchSuccess(["item-1"], batchResult)

        then:
        1 * tracer.currentSpan() >> null
        0 * span.tag(_, _)
        0 * span.end()
    }

    def "should handle onBatchDispatchFailure"() {
        given:
        def hook = new MicrometerTracingHook(tracer)
        def error = new RuntimeException("error")

        when:
        hook.onBatchDispatchFailure(["item-1"], error)

        then:
        1 * tracer.currentSpan() >> span
        1 * span.error(error)
        1 * span.end()
    }

    def "should handle onBatchDispatchFailure with null error"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onBatchDispatchFailure(["item-1"], null)

        then:
        1 * tracer.currentSpan() >> span
        0 * span.error(_)
        1 * span.end()
    }

    def "should handle onRetry"() {
        given:
        def hook = new MicrometerTracingHook(tracer)
        def cause = new RuntimeException("retry error")

        when:
        hook.onRetry("item-1", cause)

        then:
        1 * tracer.nextSpan() >> {
            def spanBuilder = Mock(Span.Builder)
            spanBuilder.name("vortex.retry") >> {
                def createdSpan = Mock(Span)
                createdSpan.tag("vortex.retry.cause", "RuntimeException") >> createdSpan
                createdSpan.start()
                tracer.withSpan(createdSpan) >> spanInScope
                createdSpan.error(cause)
                createdSpan.end()
                createdSpan
            }
            spanBuilder
        }
    }

    def "should handle onRetry with null item"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onRetry(null, new RuntimeException("error"))

        then:
        0 * tracer.nextSpan()
    }

    def "should handle onRetry with null cause"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onRetry("item-1", null)

        then:
        1 * tracer.nextSpan() >> {
            def spanBuilder = Mock(Span.Builder)
            spanBuilder.name("vortex.retry") >> {
                def createdSpan = Mock(Span)
                createdSpan.start()
                tracer.withSpan(createdSpan) >> spanInScope
                createdSpan.end()
                createdSpan
            }
            spanBuilder
        }
        0 * span.error(_)
    }

    def "should handle exceptions gracefully in onSubmit"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onSubmit("item-1")

        then:
        1 * tracer.nextSpan() >> { throw new RuntimeException("Tracing error") }
        noExceptionThrown()
    }

    def "should handle exceptions gracefully in onBatchDispatchStart"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onBatchDispatchStart(["item-1"])

        then:
        1 * tracer.nextSpan() >> { throw new RuntimeException("Tracing error") }
        noExceptionThrown()
    }

    def "should handle exceptions gracefully in onBatchDispatchSuccess"() {
        given:
        def hook = new MicrometerTracingHook(tracer)
        def batchResult = new BatchResult<>([new SuccessEvent<>("item-1")], [])

        when:
        hook.onBatchDispatchSuccess(["item-1"], batchResult)

        then:
        1 * tracer.currentSpan() >> { throw new RuntimeException("Tracing error") }
        noExceptionThrown()
    }

    def "should handle exceptions gracefully in onBatchDispatchFailure"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onBatchDispatchFailure(["item-1"], new RuntimeException("error"))

        then:
        1 * tracer.currentSpan() >> { throw new RuntimeException("Tracing error") }
        noExceptionThrown()
    }

    def "should handle exceptions gracefully in onRetry"() {
        given:
        def hook = new MicrometerTracingHook(tracer)

        when:
        hook.onRetry("item-1", new RuntimeException("error"))

        then:
        1 * tracer.nextSpan() >> { throw new RuntimeException("Tracing error") }
        noExceptionThrown()
    }
}
