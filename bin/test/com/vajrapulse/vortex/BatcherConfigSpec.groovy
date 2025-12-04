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
    
    def "should use default backpressure monitor interval when not specified"() {
        when:
        def config = BatcherConfig.builder().build()
        
        then:
        config.backpressureMonitorInterval == Duration.ofMillis(100)  // Default
    }
    
    def "should set custom backpressure monitor interval"() {
        when:
        def config = BatcherConfig.builder()
            .backpressureMonitorInterval(Duration.ofMillis(50))
            .build()
        
        then:
        config.backpressureMonitorInterval == Duration.ofMillis(50)
    }
    
    def "should reject null backpressure monitor interval"() {
        when:
        BatcherConfig.builder().backpressureMonitorInterval(null).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should reject zero backpressure monitor interval"() {
        when:
        BatcherConfig.builder().backpressureMonitorInterval(Duration.ZERO).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should reject negative backpressure monitor interval"() {
        when:
        BatcherConfig.builder().backpressureMonitorInterval(Duration.ofMillis(-1)).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should use default backpressure cache TTL when not specified"() {
        when:
        def config = BatcherConfig.builder().build()
        
        then:
        config.backpressureCacheTtl == Duration.ofMillis(50)  // Default
    }
    
    def "should set custom backpressure cache TTL"() {
        when:
        def config = BatcherConfig.builder()
            .backpressureCacheTtl(Duration.ofMillis(100))
            .build()
        
        then:
        config.backpressureCacheTtl == Duration.ofMillis(100)
    }
    
    def "should reject null backpressure cache TTL"() {
        when:
        BatcherConfig.builder().backpressureCacheTtl(null).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should reject zero backpressure cache TTL"() {
        when:
        BatcherConfig.builder().backpressureCacheTtl(Duration.ZERO).build()
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should reject negative backpressure cache TTL"() {
        when:
        BatcherConfig.builder().backpressureCacheTtl(Duration.ofMillis(-1)).build()
        
        then:
        thrown(IllegalArgumentException)
    }
}

