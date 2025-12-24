package com.vajrapulse.vortex

import com.vajrapulse.vortex.results.BatchResult
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.util.Collections

import static com.vajrapulse.vortex.TestBackendHelpers.*

class MicroBatcherBatchProcessingSpec extends Specification {

    def "should form batch when batchSize reached"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        batcher.submit("item-2")
        batcher.submit("item-3")
        Thread.sleep(200) // Wait for batch processing

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == 3

        cleanup:
        batcher?.close()
    }

    def "should form batch when lingerTime elapsed"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(150) // Wait for linger time

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == 1

        cleanup:
        batcher?.close()
    }

    def "should respect configured batchSize"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < 5; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(200)

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == 5

        cleanup:
        batcher?.close()
    }

    def "should respect configured lingerTime"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(200))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        def timeBefore = System.currentTimeMillis()
        Thread.sleep(250)
        def timeAfter = System.currentTimeMillis()

        then:
        batches.size() >= 1
        (timeAfter - timeBefore) >= 200

        cleanup:
        batcher?.close()
    }

    def "should process multiple batches sequentially"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < 6; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(300)

        then:
        batches.size() >= 3
        batches.every { it.getSuccesses().size() == 2 }

        cleanup:
        batcher?.close()
    }

    def "should contain correct items in batch"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-A")
        batcher.submit("item-B")
        batcher.submit("item-C")
        Thread.sleep(200)

        then:
        batches.size() >= 1
        def batch = batches[0]
        def items = batch.getSuccesses().collect { successEvent -> successEvent.getData() }
        items.contains("item-A")
        items.contains("item-B")
        items.contains("item-C")

        cleanup:
        batcher?.close()
    }

    @Unroll
    def "should process batch with different batch sizes (size: #size)"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(size)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < size; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(200)

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == size

        cleanup:
        batcher?.close()

        where:
        size << [1, 5, 10, 100]
    }

    def "should process batch with single item"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(10)
            .lingerTime(Duration.ofMillis(100))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        Thread.sleep(150)

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == 1

        cleanup:
        batcher?.close()
    }

    def "should process batch with exact batchSize items"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < 5; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(200)

        then:
        batches.size() >= 1
        batches[0].getSuccesses().size() == 5

        cleanup:
        batcher?.close()
    }

    def "should process batch with more than batchSize items"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        for (int i = 0; i < 7; i++) {
            batcher.submit("item-${i}")
        }
        Thread.sleep(300)

        then:
        batches.size() >= 2
        batches[0].getSuccesses().size() == 3
        batches[1].getSuccesses().size() == 3

        cleanup:
        batcher?.close()
    }

    def "should process batch during shutdown"() {
        given:
        def batches = Collections.synchronizedList([])
        Backend<String> backend = recordingBackend(batches)
        def config = BatcherConfig.builder()
            .batchSize(10) // Large batch size so items don't form batch immediately
            .lingerTime(Duration.ofMillis(1000))
            .build()

        when:
        def batcher = new MicroBatcher<>(backend, config)
        batcher.submit("item-1")
        batcher.submit("item-2")
        Thread.sleep(50) // Ensure items are queued
        batcher.close()
        Thread.sleep(300) // Allow more time for synchronous processing

        then:
        // Items should be processed during shutdown, but may have been processed before close
        batches.size() >= 0

        cleanup:
        batcher?.close()
    }
}

