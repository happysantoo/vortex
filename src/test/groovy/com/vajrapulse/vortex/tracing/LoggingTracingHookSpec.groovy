package com.vajrapulse.vortex.tracing

import com.vajrapulse.vortex.BatchResult
import com.vajrapulse.vortex.FailureEvent
import com.vajrapulse.vortex.SuccessEvent
import org.slf4j.Logger
import spock.lang.Specification

class LoggingTracingHookSpec extends Specification {

    def "should create hook with default logger"() {
        when:
        def hook = new LoggingTracingHook()

        then:
        hook != null
    }

    def "should create hook with custom logger name"() {
        when:
        def hook = new LoggingTracingHook("com.example.CustomLogger")

        then:
        hook != null
    }

    def "should create hook with custom logger"() {
        given:
        def logger = Mock(Logger)

        when:
        def hook = new LoggingTracingHook(logger)

        then:
        hook != null
    }

    def "should throw exception when logger name is null"() {
        when:
        new LoggingTracingHook((String) null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should throw exception when logger is null"() {
        when:
        new LoggingTracingHook((Logger) null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should log onSubmit at debug level"() {
        given:
        def logger = Mock(Logger)
        logger.isDebugEnabled() >> true
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onSubmit("test-item")

        then:
        1 * logger.debug("Item submitted to batcher: type={}", "String")
    }

    def "should not log onSubmit when item is null"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onSubmit(null)

        then:
        0 * logger.debug(_, _)
    }

    def "should log onBatchDispatchStart at debug level"() {
        given:
        def logger = Mock(Logger)
        logger.isDebugEnabled() >> true
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onBatchDispatchStart(["item-1", "item-2"])

        then:
        1 * logger.debug("Batch dispatch started: size={}", 2)
    }

    def "should not log onBatchDispatchStart when batch is null or empty"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onBatchDispatchStart(null)
        hook.onBatchDispatchStart([])

        then:
        0 * logger.debug(_, _)
    }

    def "should log onBatchDispatchSuccess at debug level"() {
        given:
        def logger = Mock(Logger)
        logger.isDebugEnabled() >> true
        def hook = new LoggingTracingHook(logger)
        def batchResult = new BatchResult<>(
            [new SuccessEvent<>("item-1"), new SuccessEvent<>("item-2")],
            [new FailureEvent<>("item-3", new RuntimeException("error"))]
        )

        when:
        hook.onBatchDispatchSuccess(["item-1", "item-2", "item-3"], batchResult)

        then:
        1 * logger.debug("Batch dispatch succeeded: batchSize={}, successes={}, failures={}", 3, 2, 1)
    }

    def "should not log onBatchDispatchSuccess when batch is null or empty"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)
        def batchResult = new BatchResult<>([new SuccessEvent<>("item-1")], [])

        when:
        hook.onBatchDispatchSuccess(null, batchResult)
        hook.onBatchDispatchSuccess([], batchResult)

        then:
        0 * logger.debug(_, _, _, _)
    }

    def "should log onBatchDispatchFailure at error level"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)
        def error = new RuntimeException("Batch dispatch failed")

        when:
        hook.onBatchDispatchFailure(["item-1", "item-2"], error)

        then:
        1 * logger.error("Batch dispatch failed: batchSize={}, error={}", 2, "Batch dispatch failed", error)
    }

    def "should log onBatchDispatchFailure at error level with null error"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onBatchDispatchFailure(["item-1"], null)

        then:
        1 * logger.error("Batch dispatch failed: batchSize={}, error=unknown", 1)
    }

    def "should not log onBatchDispatchFailure when batch is null or empty"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onBatchDispatchFailure(null, new RuntimeException("error"))
        hook.onBatchDispatchFailure([], new RuntimeException("error"))

        then:
        0 * logger.error(_, _)
        0 * logger.error(_, _, _)
    }

    def "should log onRetry at warn level"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)
        def cause = new RuntimeException("Retry cause")

        when:
        hook.onRetry("test-item", cause)

        then:
        1 * logger.warn("Item retry scheduled: type={}, cause={}", "String", "Retry cause", cause)
    }

    def "should log onRetry at warn level with null cause"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onRetry("test-item", null)

        then:
        1 * logger.warn("Item retry scheduled: type={}, cause=unknown", "String")
    }

    def "should not log onRetry when item is null"() {
        given:
        def logger = Mock(Logger)
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onRetry(null, new RuntimeException("error"))

        then:
        0 * logger.warn(_, _)
        0 * logger.warn(_, _, _)
    }

    def "should respect debug level for successful events"() {
        given:
        def logger = Mock(Logger)
        logger.isDebugEnabled() >> false
        def hook = new LoggingTracingHook(logger)

        when:
        hook.onSubmit("item")
        hook.onBatchDispatchStart(["item"])
        hook.onBatchDispatchSuccess(["item"], new BatchResult<>([new SuccessEvent<>("item")], []))

        then:
        0 * logger.debug(_, _)
        0 * logger.debug(_, _, _, _)
    }
}
