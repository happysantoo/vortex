package com.vajrapulse.vortex.tracing;

import com.vajrapulse.vortex.BatchTracingHook;
import com.vajrapulse.vortex.results.BatchResult;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.List;

/**
 * Micrometer Tracing integration hook for Vortex distributed tracing.
 * 
 * <p>This class provides a {@link BatchTracingHook} implementation that integrates
 * with Micrometer Tracing for distributed tracing. It creates spans for key operations
 * and propagates trace context through batch processing.
 * 
 * <p>Micrometer Tracing provides a vendor-neutral API that works with various tracing
 * backends (OpenTelemetry, Zipkin, Brave, etc.) through Micrometer's abstraction layer.
 * 
 * <p>To use this hook:
 * <pre>{@code
 * // Get Tracer from your Micrometer Tracing setup
 * Tracer tracer = ...; // From your Micrometer Tracing configuration
 * 
 * MicrometerTracingHook tracingHook = new MicrometerTracingHook(tracer);
 * 
 * BatcherConfig config = BatcherConfig.builder()
 *     .batchSize(10)
 *     .lingerTime(Duration.ofMillis(100))
 *     .tracingHook(tracingHook)
 *     .build();
 * }</pre>
 * 
 * <p><strong>Note:</strong> The tracer must be configured in your application's
 * Micrometer Tracing setup. This hook requires micrometer-tracing to be in the
 * classpath and properly configured.
 * 
 * @since 0.0.8
 */
public class MicrometerTracingHook implements BatchTracingHook {
    
    private final Tracer tracer;
    
    /**
     * Creates a new Micrometer Tracing hook.
     * 
     * @param tracer the Micrometer Tracing Tracer instance (must not be null)
     * @throws IllegalArgumentException if tracer is null
     */
    public MicrometerTracingHook(Tracer tracer) {
        if (tracer == null) {
            throw new IllegalArgumentException("Tracer cannot be null");
        }
        this.tracer = tracer;
    }
    
    @Override
    public void onSubmit(Object item) {
        if (item == null) {
            return;
        }
        
        try {
            Span span = tracer.nextSpan().name("vortex.submit");
            span.tag("vortex.item.type", item.getClass().getSimpleName());
            span.start();
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                // Span context is now current for this thread
                // The span will be ended when the batch is processed
            }
        } catch (Exception e) {
            // Silently ignore tracing errors - don't affect batch processing
        }
    }
    
    @Override
    public void onBatchDispatchStart(List<?> batchItems) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        try {
            Span span = tracer.nextSpan().name("vortex.batch.dispatch");
            span.tag("vortex.batch.size", String.valueOf(batchItems.size()));
            span.start();
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                // Span context is now current for this thread
                // The span will be ended in onBatchDispatchSuccess or onBatchDispatchFailure
            }
        } catch (Exception e) {
            // Silently ignore tracing errors - don't affect batch processing
        }
    }
    
    @Override
    public void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("vortex.batch.success.count", String.valueOf(result.getSuccesses().size()));
                currentSpan.tag("vortex.batch.failure.count", String.valueOf(result.getFailures().size()));
                currentSpan.end();
            }
        } catch (Exception e) {
            // Silently ignore tracing errors - don't affect batch processing
        }
    }
    
    @Override
    public void onBatchDispatchFailure(List<?> batchItems, Throwable error) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        try {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                if (error != null) {
                    currentSpan.error(error);
                }
                currentSpan.end();
            }
        } catch (Exception e) {
            // Silently ignore tracing errors - don't affect batch processing
        }
    }
    
    @Override
    public void onRetry(Object item, Throwable cause) {
        if (item == null) {
            return;
        }
        
        try {
            Span span = tracer.nextSpan().name("vortex.retry");
            
            if (cause != null) {
                span.tag("vortex.retry.cause", cause.getClass().getSimpleName());
            }
            
            span.start();
            try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
                if (cause != null) {
                    span.error(cause);
                }
                span.end();
            }
        } catch (Exception e) {
            // Silently ignore tracing errors - don't affect batch processing
        }
    }
}

