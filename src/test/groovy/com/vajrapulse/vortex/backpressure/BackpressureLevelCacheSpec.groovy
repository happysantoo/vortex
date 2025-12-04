package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

import java.time.Duration

class BackpressureLevelCacheSpec extends Specification {

    def "should cache backpressure level within TTL"() {
        given:
        BackpressureProvider provider = Mock()
        
        def cache = new BackpressureLevelCache(provider, Duration.ofMillis(100))

        when:
        def level1 = cache.getBackpressureLevel()
        def level2 = cache.getBackpressureLevel()
        def level3 = cache.getBackpressureLevel()

        then:
        level1 == 0.7
        level2 == 0.7
        level3 == 0.7
        // Provider should only be called once (first call)
        1 * provider.getBackpressureLevel() >> 0.7
    }

    def "should refresh cache after TTL expires"() {
        given:
        BackpressureProvider provider = Mock()
        
        def cache = new BackpressureLevelCache(provider, Duration.ofMillis(50))

        when:
        def level1 = cache.getBackpressureLevel()
        Thread.sleep(60) // Wait for TTL to expire
        def level2 = cache.getBackpressureLevel()
        Thread.sleep(60) // Wait for TTL to expire again
        def level3 = cache.getBackpressureLevel()

        then:
        level1 == 0.7
        level2 == 0.8
        level3 == 0.9
        // Provider should be called 3 times (once per expired cache)
        3 * provider.getBackpressureLevel() >>> [0.7, 0.8, 0.9]
    }

    def "should invalidate cache"() {
        given:
        BackpressureProvider provider = Mock()
        
        def cache = new BackpressureLevelCache(provider, Duration.ofMillis(1000))

        when:
        def level1 = cache.getBackpressureLevel()
        cache.invalidate()
        def level2 = cache.getBackpressureLevel()

        then:
        level1 == 0.7
        level2 == 0.8
        // Provider should be called twice (once before invalidate, once after)
        2 * provider.getBackpressureLevel() >>> [0.7, 0.8]
    }

    def "should handle concurrent access"() {
        given:
        BackpressureProvider provider = Mock()
        
        def cache = new BackpressureLevelCache(provider, Duration.ofMillis(100))
        def results = Collections.synchronizedList(new ArrayList<Double>())

        when:
        def threads = []
        10.times {
            threads << Thread.start {
                results.add(cache.getBackpressureLevel())
            }
        }
        threads.each { it.join() }

        then:
        results.size() == 10
        results.every { it == 0.7 }
        // Provider should be called at least once (may be called multiple times due to race conditions)
        (1..10) * provider.getBackpressureLevel() >> 0.7
    }
}

