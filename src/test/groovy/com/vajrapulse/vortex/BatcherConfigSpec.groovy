package com.vajrapulse.vortex

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class BatcherConfigSpec extends Specification {

    def "should create config with default values"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        config.batchSize == 10
        config.lingerTime == Duration.ofMillis(100)
        !config.atomicCommit
        config.maxConcurrency == 10
        !config.autoReplaySuccesses
    }

    def "should create config with custom values"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofSeconds(2))
            .atomicCommit(true)
            .maxConcurrency(20)
            .autoReplaySuccesses(true)
            .build()

        then:
        config.batchSize == 5
        config.lingerTime == Duration.ofSeconds(2)
        config.atomicCommit
        config.maxConcurrency == 20
        config.autoReplaySuccesses
    }

    @Unroll
    def "should reject invalid batch size: #size"() {
        when:
        BatcherConfig.builder().batchSize(size).build()

        then:
        thrown(IllegalArgumentException)

        where:
        size << [0, -1, -10]
    }

    @Unroll
    def "should reject invalid linger time: #lingerTime"() {
        when:
        BatcherConfig.builder().lingerTime(lingerTime).build()

        then:
        thrown(IllegalArgumentException)

        where:
        lingerTime << [null, Duration.ofMillis(-1), Duration.ofSeconds(-1)]
    }

    @Unroll
    def "should reject invalid max concurrency: #concurrency"() {
        when:
        BatcherConfig.builder().maxConcurrency(concurrency).build()

        then:
        thrown(IllegalArgumentException)

        where:
        concurrency << [0, -1, -5]
    }

    def "should allow zero linger time"() {
        when:
        def config = BatcherConfig.builder()
            .lingerTime(Duration.ZERO)
            .build()

        then:
        config.lingerTime == Duration.ZERO
    }

    def "builder should be chainable"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(50))
            .atomicCommit(true)
            .maxConcurrency(5)
            .autoReplaySuccesses(true)
            .build()

        then:
        config.batchSize == 3
        config.lingerTime == Duration.ofMillis(50)
        config.atomicCommit
        config.maxConcurrency == 5
        config.autoReplaySuccesses
    }

    def "should create config with perItemMetrics enabled"() {
        when:
        def config = BatcherConfig.builder()
            .perItemMetrics(true)
            .build()

        then:
        config.perItemMetrics
    }

    def "should create config with perItemMetrics disabled by default"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        !config.perItemMetrics
    }

    def "should allow perItemMetrics in builder chain"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(5)
            .perItemMetrics(true)
            .autoReplaySuccesses(false)
            .build()

        then:
        config.batchSize == 5
        config.perItemMetrics
        !config.autoReplaySuccesses
    }

    def "should create config with debugMode enabled"() {
        when:
        def config = BatcherConfig.builder()
            .debugMode(true)
            .build()

        then:
        config.debugMode
    }

    def "should create config with debugMode disabled by default"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        !config.debugMode
    }

    def "should allow debugMode in builder chain"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(5)
            .debugMode(true)
            .perItemMetrics(false)
            .build()

        then:
        config.batchSize == 5
        config.debugMode
        !config.perItemMetrics
    }

    def "should create config with retry settings"() {
        when:
        def config = BatcherConfig.builder()
            .maxRetries(3)
            .retryDelay(Duration.ofMillis(100))
            .retryableErrorPredicate { it instanceof RuntimeException }
            .build()

        then:
        config.maxRetries == 3
        config.retryDelay == Duration.ofMillis(100)
        config.retryableErrorPredicate != null
    }

    def "should create config with default retry settings"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        config.maxRetries == 0
        config.retryDelay == Duration.ZERO
        config.retryableErrorPredicate != null
    }

    @Unroll
    def "should reject invalid maxRetries: #retries"() {
        when:
        BatcherConfig.builder().maxRetries(retries).build()

        then:
        thrown(IllegalArgumentException)

        where:
        retries << [-1, -10]
    }

    @Unroll
    def "should reject invalid retryDelay: #delay"() {
        when:
        BatcherConfig.builder().retryDelay(delay).build()

        then:
        thrown(IllegalArgumentException)

        where:
        delay << [null, Duration.ofMillis(-1)]
    }

    def "should reject null retryableErrorPredicate"() {
        when:
        BatcherConfig.builder().retryableErrorPredicate(null).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "should allow zero retry delay"() {
        when:
        def config = BatcherConfig.builder()
            .retryDelay(Duration.ZERO)
            .build()

        then:
        config.retryDelay == Duration.ZERO
    }
}

