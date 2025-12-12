package com.vajrapulse.vortex.tracing;

import com.vajrapulse.vortex.BatchTracingHook;
import com.vajrapulse.vortex.results.BatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Logging-based tracing hook for Vortex batch processing.
 * 
 * <p>This class provides a {@link BatchTracingHook} implementation that logs
 * batch processing events using SLF4J. It emits logs at different levels:
 * <ul>
 *   <li><b>DEBUG</b>: Successful events (submit, batch dispatch start, batch dispatch success)</li>
 *   <li><b>WARN</b>: Retry events</li>
 *   <li><b>ERROR</b>: Failure events (batch dispatch failure)</li>
 * </ul>
 * 
 * <p>To use this hook:
 * <pre>{@code
 * LoggingTracingHook tracingHook = new LoggingTracingHook();
 * 
 * BatcherConfig config = BatcherConfig.builder()
 *     .batchSize(10)
 *     .lingerTime(Duration.ofMillis(100))
 *     .tracingHook(tracingHook)
 *     .build();
 * }</pre>
 * 
 * <p>You can also provide a custom logger name:
 * <pre>{@code
 * LoggingTracingHook tracingHook = new LoggingTracingHook("com.example.MyBatcher");
 * }</pre>
 * 
 * <p><strong>Note:</strong> This hook uses SLF4J for logging, which is already
 * a dependency of the library. No additional dependencies are required.
 * 
 * @since 0.0.8
 */
public class LoggingTracingHook implements BatchTracingHook {
    
    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(LoggingTracingHook.class);
    
    private final Logger logger;
    
    /**
     * Creates a new logging tracing hook with the default logger.
     * 
     * <p>The default logger name is {@code com.vajrapulse.vortex.tracing.LoggingTracingHook}.
     */
    public LoggingTracingHook() {
        this.logger = DEFAULT_LOGGER;
    }
    
    /**
     * Creates a new logging tracing hook with a custom logger name.
     * 
     * @param loggerName the name of the logger to use
     * @throws IllegalArgumentException if loggerName is null
     */
    public LoggingTracingHook(String loggerName) {
        if (loggerName == null) {
            throw new IllegalArgumentException("Logger name cannot be null");
        }
        this.logger = LoggerFactory.getLogger(loggerName);
    }
    
    /**
     * Creates a new logging tracing hook with a custom logger.
     * 
     * @param logger the logger to use
     * @throws IllegalArgumentException if logger is null
     */
    public LoggingTracingHook(Logger logger) {
        if (logger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }
        this.logger = logger;
    }
    
    @Override
    public void onSubmit(Object item) {
        if (item == null) {
            return;
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Item submitted to batcher: type={}", item.getClass().getSimpleName());
        }
    }
    
    @Override
    public void onBatchDispatchStart(List<?> batchItems) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        if (logger.isDebugEnabled()) {
            logger.debug("Batch dispatch started: size={}", batchItems.size());
        }
    }
    
    @Override
    public void onBatchDispatchSuccess(List<?> batchItems, BatchResult<?> result) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        if (logger.isDebugEnabled()) {
            int successCount = result != null ? result.getSuccesses().size() : 0;
            int failureCount = result != null ? result.getFailures().size() : 0;
            logger.debug("Batch dispatch succeeded: batchSize={}, successes={}, failures={}", 
                batchItems.size(), successCount, failureCount);
        }
    }
    
    @Override
    public void onBatchDispatchFailure(List<?> batchItems, Throwable error) {
        if (batchItems == null || batchItems.isEmpty()) {
            return;
        }
        
        if (error != null) {
            logger.error("Batch dispatch failed: batchSize={}, error={}", 
                batchItems.size(), error.getMessage(), error);
        } else {
            logger.error("Batch dispatch failed: batchSize={}, error=unknown", batchItems.size());
        }
    }
    
    @Override
    public void onRetry(Object item, Throwable cause) {
        if (item == null) {
            return;
        }
        
        if (cause != null) {
            logger.warn("Item retry scheduled: type={}, cause={}", 
                item.getClass().getSimpleName(), cause.getMessage(), cause);
        } else {
            logger.warn("Item retry scheduled: type={}, cause=unknown", 
                item.getClass().getSimpleName());
        }
    }
}

