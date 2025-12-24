package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import spock.lang.Specification

import java.time.Duration

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherConstructorSpec extends Specification {

    def "should reject null backend"() {
        given:
        def config = BatcherConfig.builder().build()

        when:
        new MicroBatcher<>(null, config)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null config"() {
        given:
        Backend<String> backend = { batch -> new BatchResult<>(List.of(), List.of()) }

        when:
        new MicroBatcher<>(backend, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should reject null meter registry"() {
        given:
        Backend<String> backend = { batch -> new BatchResult<>(List.of(), List.of()) }
        def config = BatcherConfig.builder().build()

        when:
        new MicroBatcher<>(backend, config, null)

        then:
        thrown(IllegalArgumentException)
    }

    def "should create batcher with default meter registry"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)

        then:
        batcher != null
        batcher.getMeterRegistry() != null
        !batcher.isClosed()

        cleanup:
        batcher?.close()
    }

    def "should create batcher with custom meter registry"() {
        given:
        Backend<String> backend = successBackend()
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build()
        def customRegistry = new SimpleMeterRegistry()

        when:
        def batcher = new MicroBatcher<>(backend, config, customRegistry)

        then:
        batcher != null
        batcher.getMeterRegistry() == customRegistry

        cleanup:
        batcher?.close()
    }
}

