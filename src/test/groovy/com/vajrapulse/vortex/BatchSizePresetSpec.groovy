package com.vajrapulse.vortex

import spock.lang.Specification

import java.time.Duration

class BatchSizePresetSpec extends Specification {

    def "should have correct batch sizes and linger times"() {
        expect:
        com.vajrapulse.vortex.config.BatchSizePreset.TINY.getBatchSize() == 5
        com.vajrapulse.vortex.config.BatchSizePreset.TINY.getLingerTime() == Duration.ofMillis(10)
        
        com.vajrapulse.vortex.config.BatchSizePreset.SMALL.getBatchSize() == 10
        com.vajrapulse.vortex.config.BatchSizePreset.SMALL.getLingerTime() == Duration.ofMillis(50)
        
        com.vajrapulse.vortex.config.BatchSizePreset.MEDIUM.getBatchSize() == 20
        com.vajrapulse.vortex.config.BatchSizePreset.MEDIUM.getLingerTime() == Duration.ofMillis(100)
        
        com.vajrapulse.vortex.config.BatchSizePreset.LARGE.getBatchSize() == 50
        com.vajrapulse.vortex.config.BatchSizePreset.LARGE.getLingerTime() == Duration.ofMillis(200)
        
        com.vajrapulse.vortex.config.BatchSizePreset.HUGE.getBatchSize() == 100
        com.vajrapulse.vortex.config.BatchSizePreset.HUGE.getLingerTime() == Duration.ofMillis(500)
    }
    
    def "should create config from preset"() {
        when:
        BatcherConfig config = com.vajrapulse.vortex.config.BatchSizePreset.MEDIUM.toConfig()
        
        then:
        config.batchSize == 20
        config.lingerTime == Duration.ofMillis(100)
        config.maxQueueSize == 40  // 2x batch size
    }
    
    def "should create config builder from preset"() {
        when:
        BatcherConfig config = com.vajrapulse.vortex.config.BatchSizePreset.SMALL.toConfigBuilder()
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
        com.vajrapulse.vortex.config.BatchSizePreset.values().length == 5
        com.vajrapulse.vortex.config.BatchSizePreset.TINY in com.vajrapulse.vortex.config.BatchSizePreset.values()
        com.vajrapulse.vortex.config.BatchSizePreset.SMALL in com.vajrapulse.vortex.config.BatchSizePreset.values()
        com.vajrapulse.vortex.config.BatchSizePreset.MEDIUM in com.vajrapulse.vortex.config.BatchSizePreset.values()
        com.vajrapulse.vortex.config.BatchSizePreset.LARGE in com.vajrapulse.vortex.config.BatchSizePreset.values()
        com.vajrapulse.vortex.config.BatchSizePreset.HUGE in com.vajrapulse.vortex.config.BatchSizePreset.values()
    }
}

