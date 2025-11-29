package com.vajrapulse.vortex.example;

import com.vajrapulse.vortex.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Example demonstrating the BatchTracingHook interface for observability.
 * 
 * This example shows how to:
 * - Implement a custom tracing hook
 * - Configure it in BatcherConfig
 * - Track submit, batch dispatch, and retry events
 * - Integrate with distributed tracing systems (OpenTelemetry, Zipkin, etc.)
 */
public class TracingExample {
    
    /**
     * Simple tracing hook implementation that logs events.
     * In a real application, this would integrate with OpenTelemetry, Zipkin, etc.
     */
    static class LoggingTracingHook implements BatchTracingHook {
        @Override
        public void onSubmit(Object item) {
            System.out.println("[TRACE] Item submitted: " + item);
            // In real app: create span, add tags, etc.
        }
        
        @Override
        public void onBatchDispatchStart(List<?> batchItems) {
            System.out.println("[TRACE] Batch dispatch started: " + batchItems.size() + " items");
            // In real app: start batch span, record batch size metric
        }
        
        @Override
        public void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {
            System.out.println("[TRACE] Batch dispatch succeeded: " + 
                result.getSuccesses().size() + " successes, " + 
                result.getFailures().size() + " failures");
            // In real app: record success, add result tags, close span
        }
        
        @Override
        public void onBatchDispatchFailure(List<?> batchItems, Throwable error) {
            System.out.println("[TRACE] Batch dispatch failed: " + error.getClass().getSimpleName() + 
                " - " + error.getMessage());
            // In real app: record error, add error tags, close span with error status
        }
        
        @Override
        public void onRetry(Object item, Throwable cause) {
            System.out.println("[TRACE] Retrying item: " + item + " (cause: " + 
                cause.getClass().getSimpleName() + ")");
            // In real app: create retry span, link to original span, add retry count
        }
    }
    
    /**
     * OpenTelemetry-style tracing hook (pseudo-implementation).
     * Shows how you would integrate with actual OpenTelemetry SDK.
     */
    static class OpenTelemetryTracingHook implements BatchTracingHook {
        // In real implementation, you would inject Tracer from OpenTelemetry SDK
        // private final Tracer tracer = openTelemetry.getTracer("vortex-batcher");
        
        @Override
        public void onSubmit(Object item) {
            // Span span = tracer.spanBuilder("vortex.submit")
            //     .setAttribute("item.type", item.getClass().getSimpleName())
            //     .startSpan();
            // try (Scope scope = span.makeCurrent()) {
            //     // span context propagated
            // }
            System.out.println("[OTEL] Span created for submit: " + item);
        }
        
        @Override
        public void onBatchDispatchStart(List<?> batchItems) {
            // Span span = tracer.spanBuilder("vortex.batch.dispatch")
            //     .setAttribute("batch.size", batchItems.size())
            //     .startSpan();
            System.out.println("[OTEL] Batch span started: size=" + batchItems.size());
        }
        
        @Override
        public void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {
            // span.setAttribute("success.count", result.getSuccesses().size());
            // span.setAttribute("failure.count", result.getFailures().size());
            // span.setStatus(StatusCode.OK);
            // span.end();
            System.out.println("[OTEL] Batch span completed successfully");
        }
        
        @Override
        public void onBatchDispatchFailure(List<?> batchItems, Throwable error) {
            // span.recordException(error);
            // span.setStatus(StatusCode.ERROR, error.getMessage());
            // span.end();
            System.out.println("[OTEL] Batch span failed: " + error.getMessage());
        }
        
        @Override
        public void onRetry(Object item, Throwable cause) {
            // Span retrySpan = tracer.spanBuilder("vortex.retry")
            //     .setParent(Context.current().with(span))
            //     .setAttribute("retry.cause", cause.getClass().getSimpleName())
            //     .startSpan();
            System.out.println("[OTEL] Retry span created for: " + item);
        }
    }
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== Tracing Example: Logging Hook ===\n");
        
        // Backend that processes strings
        Backend<String> backend = batch -> {
            System.out.println("Backend processing batch of " + batch.size() + " items");
            List<SuccessEvent<String>> successes = new ArrayList<>();
            for (String item : batch) {
                successes.add(new SuccessEvent<>(item));
            }
            return new BatchResult<>(successes, List.of());
        };
        
        // Configure with logging tracing hook
        BatcherConfig config = BatcherConfig.builder()
            .batchSize(3)
            .lingerTime(Duration.ofMillis(100))
            .tracingHook(new LoggingTracingHook())
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
        
        System.out.println("\n=== Tracing Example: OpenTelemetry-style Hook ===\n");
        
        // Example with OpenTelemetry-style hook
        BatcherConfig otelConfig = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .tracingHook(new OpenTelemetryTracingHook())
            .build();
        
        try (MicroBatcher<String> otelBatcher = new MicroBatcher<>(backend, otelConfig)) {
            System.out.println("Submitting 3 items with OTEL-style tracing...\n");
            
            for (int itemIndex = 0; itemIndex < 3; itemIndex++) {
                otelBatcher.submit("OTEL-Item-" + itemIndex);
            }
            
            Thread.sleep(300); // Wait for processing
        }
        
        System.out.println("\n=== Example with Retry Tracing ===\n");
        
        // Backend that fails on certain items to trigger retries
        Backend<String> retryBackend = batch -> {
            List<SuccessEvent<String>> successes = new ArrayList<>();
            List<FailureEvent<String>> failures = new ArrayList<>();
            
            for (String item : batch) {
                if (item.contains("fail")) {
                    failures.add(new FailureEvent<>(item, new RuntimeException("Transient error")));
                } else {
                    successes.add(new SuccessEvent<>(item));
                }
            }
            return new BatchResult<>(successes, failures);
        };
        
        BatcherConfig retryConfig = BatcherConfig.builder()
            .batchSize(2)
            .lingerTime(Duration.ofMillis(50))
            .maxRetries(2)
            .retryDelay(Duration.ofMillis(10))
            .retryableErrorPredicate(t -> t instanceof RuntimeException)
            .tracingHook(new LoggingTracingHook())
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

