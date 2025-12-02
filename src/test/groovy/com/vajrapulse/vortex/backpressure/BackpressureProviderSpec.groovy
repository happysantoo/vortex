package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

class BackpressureProviderSpec extends Specification {

    def "should return backpressure level between 0.0 and 1.0"() {
        given:
        BackpressureProvider provider = new BackpressureProvider() {
            @Override
            double getBackpressureLevel() {
                return 0.5
            }
            
            @Override
            String getSourceName() {
                return "Test Provider"
            }
        }
        
        when:
        double level = provider.getBackpressureLevel()
        
        then:
        level >= 0.0
        level <= 1.0
        level == 0.5
    }
    
    def "should return source name"() {
        given:
        BackpressureProvider provider = new BackpressureProvider() {
            @Override
            double getBackpressureLevel() {
                return 0.0
            }
            
            @Override
            String getSourceName() {
                return "Test Provider"
            }
        }
        
        when:
        String name = provider.getSourceName()
        
        then:
        name == "Test Provider"
    }
    
    def "should return empty details by default"() {
        given:
        BackpressureProvider provider = new BackpressureProvider() {
            @Override
            double getBackpressureLevel() {
                return 0.0
            }
            
            @Override
            String getSourceName() {
                return "Test"
            }
        }
        
        when:
        Map<String, Object> details = provider.getDetails()
        
        then:
        details != null
        details.isEmpty()
    }
}

