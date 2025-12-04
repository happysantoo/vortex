package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureStrategySpec extends Specification {

    def "should handle item with backpressure context"() {
        given:
        BackpressureProvider provider = Mock()
        provider.getBackpressureLevel() >> 0.5
        provider.getSourceName() >> "Test Provider"
        
        BackpressureContext<String> bpContext = new BackpressureContext<>(
            "test-item", 0.5, provider
        )
        
        BackpressureStrategy<String> strategy = new BackpressureStrategy<String>() {
            @Override
            BackpressureResult<String> handle(BackpressureContext<String> ctx) {
                return BackpressureResult.accept(ctx.item())
            }
        }
        
        when:
        BackpressureResult<String> result = strategy.handle(bpContext)
        
        then:
        result != null
        result.action() == BackpressureAction.ACCEPT
        result.item() == "test-item"
        result.reason() == null
    }
    
    def "should return NaN for default getThreshold when not overridden"() {
        given:
        BackpressureStrategy<String> strategy = new BackpressureStrategy<String>() {
            @Override
            BackpressureResult<String> handle(BackpressureContext<String> ctx) {
                return BackpressureResult.accept(ctx.item())
            }
        }
        
        when:
        double threshold = strategy.getThreshold()
        
        then:
        Double.isNaN(threshold)  // Default implementation returns NaN
    }
}

