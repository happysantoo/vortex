package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class LifecycleAwareStrategySpec extends Specification {

    def "should have default implementation for onBackpressureActive"() {
        given:
        BackpressureProvider testProvider = Mock()
        testProvider.getSourceName() >> "Test Provider"
        
        // Create a simple implementation that doesn't override onBackpressureActive
        LifecycleAwareStrategy<String> strategy = new LifecycleAwareStrategy<String>() {
            @Override
            BackpressureResult<String> handle(BackpressureContext<String> context) {
                return BackpressureResult.accept(context.item())
            }
            
            @Override
            void onBackpressureEntered(BackpressureProvider p) {
                // Required implementation
            }
            
            @Override
            void onBackpressureResolved(BackpressureProvider p) {
                // Required implementation
            }
        }
        
        when:
        strategy.onBackpressureActive(testProvider)
        
        then:
        // Default implementation should do nothing (no exception)
        noExceptionThrown()
    }
    
    def "should allow overriding onBackpressureActive"() {
        given:
        BackpressureProvider testProvider = Mock()
        testProvider.getSourceName() >> "Test Provider"
        testProvider.getBackpressureLevel() >> 0.5
        
        def activeCalled = false
        LifecycleAwareStrategy<String> strategy = new LifecycleAwareStrategy<String>() {
            @Override
            BackpressureResult<String> handle(BackpressureContext<String> context) {
                return BackpressureResult.accept(context.item())
            }
            
            @Override
            void onBackpressureEntered(BackpressureProvider p) {
                // Required implementation
            }
            
            @Override
            void onBackpressureResolved(BackpressureProvider p) {
                // Required implementation
            }
            
            @Override
            void onBackpressureActive(BackpressureProvider p) {
                activeCalled = true
            }
        }
        
        when:
        strategy.onBackpressureActive(testProvider)
        
        then:
        activeCalled
    }
}

