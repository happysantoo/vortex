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
}

