package com.vajrapulse.vortex.backpressure

import spock.lang.Specification

import java.util.Collections

class InMemoryOverflowStorageSpec extends Specification {

    def "should add and poll items"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        when:
        storage.add("item1")
        storage.add("item2")
        String item1 = storage.poll()
        String item2 = storage.poll()
        
        then:
        item1 == "item1"
        item2 == "item2"
        storage.isEmpty()
    }
    
    def "should return null when polling empty storage"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        when:
        String item = storage.poll()
        
        then:
        item == null
    }
    
    def "should report correct size"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        expect:
        storage.size() == 0
        storage.isEmpty()
        
        when:
        storage.add("item1")
        
        then:
        storage.size() == 1
        !storage.isEmpty()
        
        when:
        storage.add("item2")
        
        then:
        storage.size() == 2
        
        when:
        storage.poll()
        
        then:
        storage.size() == 1
    }
    
    def "should throw exception when adding to full storage"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(2)
        storage.add("item1")
        storage.add("item2")
        
        when:
        storage.add("item3")
        
        then:
        thrown(IllegalStateException)
    }
    
    def "should throw exception for null item"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        when:
        storage.add(null)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should clear all items"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        storage.add("item1")
        storage.add("item2")
        
        when:
        storage.clear()
        
        then:
        storage.isEmpty()
        storage.size() == 0
        storage.poll() == null
    }
    
    def "should throw exception for zero capacity"() {
        when:
        new InMemoryOverflowStorage<>(0)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should throw exception for negative capacity"() {
        when:
        new InMemoryOverflowStorage<>(-1)
        
        then:
        thrown(IllegalArgumentException)
    }
    
    def "should return max capacity"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(100)
        
        when:
        int capacity = storage.getMaxCapacity()
        
        then:
        capacity == 100
    }
    
    def "should handle concurrent access"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(1000)
        def threads = []
        def results = Collections.synchronizedList(new ArrayList())
        
        when:
        // Add items from multiple threads
        10.times { threadIndex ->
            threads << Thread.start {
                100.times { itemIndex ->
                    storage.add("item-${threadIndex}-${itemIndex}")
                }
            }
        }
        
        threads.each { it.join() }
        
        // Poll items from multiple threads
        threads.clear()
        10.times {
            threads << Thread.start {
                100.times {
                    String item = storage.poll()
                    if (item != null) {
                        results.add(item)
                    }
                }
            }
        }
        
        threads.each { it.join() }
        
        then:
        results.size() == 1000
        storage.isEmpty()
    }
    
    def "should handle adding to full storage gracefully"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(2)
        storage.add("item1")
        storage.add("item2")
        
        when:
        storage.add("item3")
        
        then:
        thrown(IllegalStateException)
        storage.size() == 2
    }
    
    def "should handle multiple clear operations"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        storage.add("item1")
        storage.add("item2")
        
        when:
        storage.clear()
        storage.clear()  // Second clear
        
        then:
        storage.isEmpty()
        storage.size() == 0
    }
    
    def "should handle poll on empty storage multiple times"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        when:
        String item1 = storage.poll()
        String item2 = storage.poll()
        String item3 = storage.poll()
        
        then:
        item1 == null
        item2 == null
        item3 == null
        storage.isEmpty()
    }
    
    def "should maintain FIFO order"() {
        given:
        InMemoryOverflowStorage<String> storage = new InMemoryOverflowStorage<>(10)
        
        when:
        storage.add("item1")
        storage.add("item2")
        storage.add("item3")
        String first = storage.poll()
        String second = storage.poll()
        String third = storage.poll()
        
        then:
        first == "item1"
        second == "item2"
        third == "item3"
    }
}

