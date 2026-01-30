package com.vajrapulse.vortex.internal

import com.vajrapulse.vortex.BatcherConfig
import spock.lang.Specification

import java.time.Duration

class RetryManagerBackoffSpec extends Specification {

    def "computeRetryDelayMillis returns 0 for zero base delay"() {
        expect:
        RetryManager.computeRetryDelayMillis(Duration.ZERO, 1, BatcherConfig.RetryBackoffStrategy.FIXED, Duration.ofSeconds(30)) == 0
        RetryManager.computeRetryDelayMillis(Duration.ZERO, 5, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofSeconds(30)) == 0
    }

    def "computeRetryDelayMillis uses fixed delay regardless of attempt"() {
        expect:
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 1, BatcherConfig.RetryBackoffStrategy.FIXED, Duration.ofSeconds(30)) == 100
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 2, BatcherConfig.RetryBackoffStrategy.FIXED, Duration.ofSeconds(30)) == 100
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 10, BatcherConfig.RetryBackoffStrategy.FIXED, Duration.ofSeconds(30)) == 100
    }

    def "computeRetryDelayMillis uses exponential backoff and caps at max delay"() {
        expect:
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 1, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 100
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 2, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 200
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 3, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 400
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 4, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 800
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 5, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 1000
        RetryManager.computeRetryDelayMillis(Duration.ofMillis(100), 6, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofMillis(1000)) == 1000
    }

    def "computeRetryDelayMillis saturates safely for very large attempts"() {
        expect:
        RetryManager.computeRetryDelayMillis(Duration.ofSeconds(1), 1000, BatcherConfig.RetryBackoffStrategy.EXPONENTIAL, Duration.ofSeconds(30)) == 30_000
    }
}

