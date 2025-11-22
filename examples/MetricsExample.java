package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating metrics collection and monitoring.
 */
public class MetricsExample {
    
    public static void main(String[] args) throws Exception {
        Backend<String> backend = batch -> {
            try {
                Thread.sleep(50); // Simulate processing time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(5)
            .lingerTime(Duration.ofMillis(100))
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config, registry)) {
            List<CompletableFuture<BatchResult<String>>> futures = new ArrayList<>();
            
            for (int i = 0; i < 20; i++) {
                futures.add(batcher.submit("Item-" + i));
            }
            
            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
            
            // Print metrics
            System.out.println("=== Metrics ===");
            System.out.println("Requests submitted: " + 
                registry.counter("vortex.requests.submitted").count());
            System.out.println("Batches dispatched: " + 
                registry.counter("vortex.batches.dispatched").count());
            System.out.println("Requests succeeded: " + 
                registry.counter("vortex.requests.succeeded").count());
            System.out.println("Requests failed: " + 
                registry.counter("vortex.requests.failed").count());
            System.out.println("Queue depth: " + 
                registry.gauge("vortex.queue.depth", 0.0));
            System.out.println("Avg batch dispatch latency: " + 
                registry.timer("vortex.batch.dispatch.latency").mean(java.util.concurrent.TimeUnit.MILLISECONDS) + " ms");
            System.out.println("Avg request wait latency: " + 
                registry.timer("vortex.request.wait.latency").mean(java.util.concurrent.TimeUnit.MILLISECONDS) + " ms");
        }
    }
}

