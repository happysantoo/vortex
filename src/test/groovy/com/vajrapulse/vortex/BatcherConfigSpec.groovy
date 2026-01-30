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
        !config.autoReplaySuccesses
    }

    def "should create config with custom values"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofSeconds(2))
            .atomicCommit(true)
            .autoReplaySuccesses(true)
            .build()

        then:
        config.batchSize == 5
        config.lingerTime == Duration.ofSeconds(2)
        config.atomicCommit
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
            .autoReplaySuccesses(true)
            .build()

        then:
        config.batchSize == 3
        config.lingerTime == Duration.ofMillis(50)
        config.atomicCommit
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

    def "should default retry backoff strategy to FIXED and max delay to 30s"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        config.retryBackoffStrategy == BatcherConfig.RetryBackoffStrategy.FIXED
        config.retryMaxDelay == Duration.ofSeconds(30)
    }

    def "should allow configuring exponential retry backoff strategy and max delay"() {
        when:
        def config = BatcherConfig.builder()
            .retryBackoffStrategy(BatcherConfig.RetryBackoffStrategy.EXPONENTIAL)
            .retryMaxDelay(Duration.ofSeconds(5))
            .build()

        then:
        config.retryBackoffStrategy == BatcherConfig.RetryBackoffStrategy.EXPONENTIAL
        config.retryMaxDelay == Duration.ofSeconds(5)
    }

    def "should reject null retry backoff strategy"() {
        when:
        BatcherConfig.builder().retryBackoffStrategy(null).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null retry max delay"() {
        when:
        BatcherConfig.builder().retryMaxDelay(null).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "should build config with all options set"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(20)
            .lingerTime(Duration.ofMillis(200))
            .atomicCommit(true)
            .autoReplaySuccesses(true)
            .perItemMetrics(true)
            .debugMode(true)
            .maxRetries(5)
            .retryDelay(Duration.ofMillis(150))
            .retryableErrorPredicate { it instanceof IOException }
            .build()

        then:
        config.batchSize == 20
        config.lingerTime == Duration.ofMillis(200)
        config.atomicCommit
        config.autoReplaySuccesses
        config.perItemMetrics
        config.debugMode
        config.maxRetries == 5
        config.retryDelay == Duration.ofMillis(150)
        config.retryableErrorPredicate != null
    }

    def "should build config multiple times with builder"() {
        given:
        def builder = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(50))

        when:
        def config1 = builder.build()
        def config2 = builder
            .batchSize(10)
            .build()

        then:
        config1.batchSize == 5
        config2.batchSize == 10
        config1 != config2
    }

    def "should build config with zero values"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(1)
            .lingerTime(Duration.ZERO)
            .maxRetries(0)
            .retryDelay(Duration.ZERO)
            .build()

        then:
        config.batchSize == 1
        config.lingerTime == Duration.ZERO
        config.maxRetries == 0
        config.retryDelay == Duration.ZERO
    }

    def "should default maxQueueSize to 2x batchSize"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(10)
            .build()

        then:
        config.maxQueueSize == 20 // 2x batchSize
    }

    def "should allow custom maxQueueSize"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(10)
            .maxQueueSize(50)
            .build()

        then:
        config.maxQueueSize == 50
    }

    def "should allow maxQueueSize equal to batchSize"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(10)
            .maxQueueSize(10)
            .build()

        then:
        config.maxQueueSize == 10
    }

    @Unroll
    def "should reject maxQueueSize less than batchSize: batchSize=#batchSize, maxQueueSize=#maxQueueSize"() {
        when:
        BatcherConfig.builder()
            .batchSize(batchSize)
            .maxQueueSize(maxQueueSize)
            .build()

        then:
        thrown(IllegalArgumentException)

        where:
        batchSize | maxQueueSize
        10        | 9
        10        | 5
        10        | 0
        5         | 4
        100       | 50
    }

    def "should include maxQueueSize in builder chain"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(5)
            .maxQueueSize(25)
            .lingerTime(Duration.ofMillis(100))
            .build()

        then:
        config.batchSize == 5
        config.maxQueueSize == 25
        config.lingerTime == Duration.ofMillis(100)
    }

    def "should build config with all options including maxQueueSize"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(20)
            .lingerTime(Duration.ofMillis(200))
            .maxQueueSize(100)
            .atomicCommit(true)
            .autoReplaySuccesses(true)
            .perItemMetrics(true)
            .debugMode(true)
            .maxRetries(5)
            .retryDelay(Duration.ofMillis(150))
            .retryableErrorPredicate { it instanceof IOException }
            .build()

        then:
        config.batchSize == 20
        config.maxQueueSize == 100
        config.lingerTime == Duration.ofMillis(200)
        config.atomicCommit
        config.autoReplaySuccesses
        config.perItemMetrics
        config.debugMode
        config.maxRetries == 5
        config.retryDelay == Duration.ofMillis(150)
        config.retryableErrorPredicate != null
    }
    
    def "should use default maxConcurrentBatches when not specified"() {
        when:
        def config = BatcherConfig.builder().build()
        
        then:
        config.maxConcurrentBatches == 0  // Default: unlimited
    }
    
    def "should set custom maxConcurrentBatches"() {
        when:
        def config = BatcherConfig.builder()
            .maxConcurrentBatches(8)
            .build()
        
        then:
        config.maxConcurrentBatches == 8
    }
    
    def "should reject negative maxConcurrentBatches"() {
        when:
        BatcherConfig.builder().maxConcurrentBatches(-1).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should allow maxConcurrentBatches of 0 for unlimited"() {
        when:
        def config = BatcherConfig.builder()
            .maxConcurrentBatches(0)
            .build()
        
        then:
        config.maxConcurrentBatches == 0
    }
    
    def "should allow maxConcurrentBatches in builder chain"() {
        when:
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .maxConcurrentBatches(5)
            .build()
        
        then:
        config.batchSize == 10
        config.lingerTime == Duration.ofMillis(100)
        config.maxConcurrentBatches == 5
    }

    def "should default earlyConcurrentBatchRejection to false"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        !config.earlyConcurrentBatchRejection
    }

    def "should enable earlyConcurrentBatchRejection when configured"() {
        when:
        def config = BatcherConfig.builder()
            .earlyConcurrentBatchRejection(true)
            .build()

        then:
        config.earlyConcurrentBatchRejection
    }

    def "should default circuit breaker to disabled"() {
        when:
        def config = BatcherConfig.builder().build()

        then:
        !config.circuitBreakerEnabled
        config.circuitBreakerFailureThreshold == 5
        config.circuitBreakerOpenDuration == Duration.ofSeconds(30)
    }

    def "should enable circuit breaker with custom threshold and duration"() {
        when:
        def config = BatcherConfig.builder()
            .circuitBreakerEnabled(true)
            .circuitBreakerFailureThreshold(3)
            .circuitBreakerOpenDuration(Duration.ofSeconds(60))
            .build()

        then:
        config.circuitBreakerEnabled
        config.circuitBreakerFailureThreshold == 3
        config.circuitBreakerOpenDuration == Duration.ofSeconds(60)
    }

    def "should reject invalid circuit breaker failure threshold"() {
        when:
        BatcherConfig.builder().circuitBreakerFailureThreshold(0).build()

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject invalid circuit breaker open duration"() {
        when:
        BatcherConfig.builder().circuitBreakerOpenDuration(Duration.ofMillis(-1)).build()

        then:
        thrown(IllegalArgumentException)
    }
}

