package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;
import com.vajrapulse.vortex.tracing.LoggingTracingHook;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating the BatchTracingHook interface for observability.
 * 
 * This example shows how to:
 * - Use LoggingTracingHook for simple log-based tracing
 * - Use MicrometerTracingHook for distributed tracing
 * - Configure tracing hooks in BatcherConfig
 * - Track submit, batch dispatch, and retry events
 */
public class TracingExample {
    
    /**
     * Micrometer Tracing hook example.
     * Shows how to use MicrometerTracingHook with Micrometer Tracing.
     * 
     * Note: This is a simplified example. In a real application, you would:
     * 1. Configure Micrometer Tracing in your application (e.g., via Spring Boot or manual setup)
     * 2. Get the Tracer instance from your Micrometer Tracing configuration
     * 3. Pass it to MicrometerTracingHook
     * 
     * Example with Spring Boot:
     * <pre>{@code
     * @Autowired
     * private Tracer tracer;
     * 
     * MicrometerTracingHook tracingHook = new MicrometerTracingHook(tracer);
     * }</pre>
     */
    static class MicrometerTracingHookExample {
        // In real implementation, you would inject Tracer from Micrometer Tracing
        // private final Tracer tracer = ...; // From your Micrometer Tracing setup
        // private final MicrometerTracingHook hook = new MicrometerTracingHook(tracer);
        
        // This example just shows the concept - actual usage requires Micrometer Tracing setup
        public static void demonstrateUsage() {
            System.out.println("[MICROMETER] To use MicrometerTracingHook:");
            System.out.println("1. Configure Micrometer Tracing in your application");
            System.out.println("2. Get Tracer instance from Micrometer Tracing");
            System.out.println("3. Create: MicrometerTracingHook hook = new MicrometerTracingHook(tracer);");
            System.out.println("4. Configure: config.tracingHook(hook)");
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Tracing Example: Logging Hook ===\n");
        
        // Backend that processes strings
        Backend<String> backend = batch -> {
            System.out.println("Backend processing batch of " + batch.size() + " items");
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
            }
            return new com.vajrapulse.vortex.results.BatchResult<>(successes, List.of());
        };
        
        // Configure with LoggingTracingHook (uses SLF4J, logs at DEBUG/WARN/ERROR levels)
        LoggingTracingHook loggingHook = new LoggingTracingHook();
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .tracingHook(loggingHook)
            .build();
        
        try (MicroBatcher<String> batcher = new MicroBatcher<>(backend, config)) {
            System.out.println("Submitting 5 items...\n");
            
            for (int itemIndex = 0; itemIndex < 5; itemIndex++) {
                CompletableFuture<BatchResult<String>> future = batcher.submit("Item-" + itemIndex);
                future.thenAccept(result -> 
                    System.out.println("Result: " + result.getSuccesses().size() + " successes\n")
                );
            }
            
            Thread.sleep(500); // Wait for processing
        }
        
        System.out.println("\n=== Tracing Example: Micrometer Tracing Hook ===\n");
        
        // Example showing how to use MicrometerTracingHook
        // Note: This requires Micrometer Tracing to be configured in your application
        MicrometerTracingHookExample.demonstrateUsage();
        
        // In a real application with Micrometer Tracing configured:
        // Tracer tracer = ...; // From your Micrometer Tracing setup
        // MicrometerTracingHook micrometerHook = new MicrometerTracingHook(tracer);
        // BatcherConfig micrometerConfig = BatcherConfig.builder()
        //     .batchSize(2)
        //     .lingerTime(Duration.ofMillis(50))
        //     .tracingHook(micrometerHook)
        //     .build();
        
        System.out.println("\n=== Example with Retry Tracing ===\n");
        
        // Backend that fails on certain items to trigger retries
        Backend<String> retryBackend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("fail")) {
                    failures.add(new com.vajrapulse.vortex.results.FailureEvent<>(item, new RuntimeException("Transient error")));
                } else {
                    successes.add(new com.vajrapulse.vortex.results.SuccessEvent<>(item));
                }
            }
            return new com.vajrapulse.vortex.results.BatchResult<>(successes, failures);
        };
        
        // Use LoggingTracingHook with custom logger name
        LoggingTracingHook retryHook = new LoggingTracingHook("com.example.RetryBatcher");
        BatcherConfig retryConfig = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .maxRetries(2)
            .retryDelay(Duration.ofMillis(10))
            .retryableErrorPredicate(t -> t instanceof RuntimeException)
            .tracingHook(retryHook)
            .build();
        
        try (MicroBatcher<String> retryBatcher = new MicroBatcher<>(retryBackend, retryConfig)) {
            System.out.println("Submitting items (some will fail and retry)...\n");
            
            retryBatcher.submit("success-1");
            retryBatcher.submit("fail-item");
            retryBatcher.submit("success-2");
            
            Thread.sleep(500); // Wait for processing and retries
        }
        
        System.out.println("\n=== Example Complete ===");
    }
}

