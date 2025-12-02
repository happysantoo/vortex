package com.vajrapulse.vortex

import spock.lang.Specification

import java.time.Duration

class BatchSizePresetSpec extends Specification {

    def "should have correct batch sizes and linger times"() {
        expect:
        BatchSizePreset.TINY.getBatchSize() == 5
        BatchSizePreset.TINY.getLingerTime() == Duration.ofMillis(10)
        
        BatchSizePreset.SMALL.getBatchSize() == 10
        BatchSizePreset.SMALL.getLingerTime() == Duration.ofMillis(50)
        
        BatchSizePreset.MEDIUM.getBatchSize() == 20
        BatchSizePreset.MEDIUM.getLingerTime() == Duration.ofMillis(100)
        
        BatchSizePreset.LARGE.getBatchSize() == 50
        BatchSizePreset.LARGE.getLingerTime() == Duration.ofMillis(200)
        
        BatchSizePreset.HUGE.getBatchSize() == 100
        BatchSizePreset.HUGE.getLingerTime() == Duration.ofMillis(500)
    }
    
    def "should create config from preset"() {
        when:
        BatcherConfig config = BatchSizePreset.MEDIUM.toConfig()
        
        then:
        config.batchSize == 20
        config.lingerTime == Duration.ofMillis(100)
        config.maxQueueSize == 40  // 2x batch size
    }
    
    def "should create config builder from preset"() {
        when:
        BatcherConfig config = BatchSizePreset.SMALL.toConfigBuilder()
            .maxRetries(3)
            .build()
        
        then:
        config.batchSize == 10
        config.lingerTime == Duration.ofMillis(50)
        config.maxRetries == 3
        config.maxQueueSize == 20  // 2x batch size
    }
    
    def "should have all presets defined"() {
        expect:
        BatchSizePreset.values().length == 5
        BatchSizePreset.TINY in BatchSizePreset.values()
        BatchSizePreset.SMALL in BatchSizePreset.values()
        BatchSizePreset.MEDIUM in BatchSizePreset.values()
        BatchSizePreset.LARGE in BatchSizePreset.values()
        BatchSizePreset.HUGE in BatchSizePreset.values()
    }
}

