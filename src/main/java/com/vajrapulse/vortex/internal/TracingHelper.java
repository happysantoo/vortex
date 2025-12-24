package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatchTracingHook;
import com.vajrapulse.vortex.results.BatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Helper class for safely invoking tracing hooks without interfering with core batching logic.
 * All tracing errors are swallowed and optionally logged when debug mode is enabled.
 */
public class TracingHelper {
    private static final Logger logger = LoggerFactory.getLogger(TracingHelper.class);
    
    private final BatchTracingHook tracingHook;
    private final boolean debugMode;
    
    public TracingHelper(BatchTracingHook tracingHook, boolean debugMode) {
        this.tracingHook = tracingHook;
        this.debugMode = debugMode;
    }
    
    /**
     * Invokes tracing hook for submit events in a safe, centralized way.
     * Any tracing errors are swallowed and optionally logged when debug mode
     * is enabled. This ensures tracing never interferes with core batching.
     * 
     * @param item the item being submitted
     * @param <T> the type of item
     */
    public <T> void safeOnSubmit(T item) {
        if (tracingHook == null || item == null) {
            return;
        }
        try {
            tracingHook.onSubmit(item);
        } catch (Exception e) {
            if (debugMode) {
                logger.debug("Tracing hook onSubmit failed", e);
            }
        }
    }
    
    /**
     * Invokes tracing hook for batch dispatch start events.
     * 
     * @param dataList the list of items in the batch
     * @param <T> the type of item
     */
    public <T> void safeOnBatchDispatchStart(List<T> dataList) {
        if (tracingHook == null) {
            return;
        }
        try {
            tracingHook.onBatchDispatchStart(dataList);
        } catch (Exception e) {
            if (debugMode) {
                logger.debug("Tracing hook onBatchDispatchStart failed", e);
            }
        }
    }
    
    /**
     * Invokes tracing hook for batch dispatch success events.
     * 
     * @param dataList the list of items in the batch
     * @param result the batch result
     * @param <T> the type of item
     */
    public <T> void safeOnBatchDispatchSuccess(List<T> dataList, BatchResult<T> result) {
        if (tracingHook == null) {
            return;
        }
        try {
            tracingHook.onBatchDispatchSuccess(dataList, result);
        } catch (Exception e) {
            if (debugMode) {
                logger.debug("Tracing hook onBatchDispatchSuccess failed", e);
            }
        }
    }
    
    /**
     * Invokes tracing hook for batch dispatch failure events.
     * 
     * @param dataList the list of items in the batch
     * @param error the error that caused the failure
     * @param <T> the type of item
     */
    public <T> void safeOnBatchDispatchFailure(List<T> dataList, Throwable error) {
        if (tracingHook == null) {
            return;
        }
        try {
            tracingHook.onBatchDispatchFailure(dataList, error);
        } catch (Exception e) {
            if (debugMode) {
                logger.debug("Tracing hook onBatchDispatchFailure failed", e);
            }
        }
    }
}

