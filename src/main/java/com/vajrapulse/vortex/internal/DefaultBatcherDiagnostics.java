package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.health.BatcherDiagnostics;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;

/**
 * Default implementation of {@link BatcherDiagnostics} that provides
 * a read-only view of the current MicroBatcher state.
 *
 * <p>This class is intended for internal use only. It is created by
 * {@link com.vajrapulse.vortex.MicroBatcher#diagnostics()} to provide
 * diagnostics information.
 *
 * <p><strong>Note:</strong> This class is in the {@code internal} package
 * and should not be used directly by application code. Use
 * {@link com.vajrapulse.vortex.MicroBatcher#diagnostics()} instead.
 *
 * @param <T> the type of items being processed
 */
public class DefaultBatcherDiagnostics<T> implements BatcherDiagnostics {
    private final boolean closed;
    private final BatcherConfig config;
    private final BlockingQueue<PendingRequest<T>> queue;

    /**
     * Creates a new DefaultBatcherDiagnostics instance.
     *
     * @param closed whether the batcher is closed
     * @param config the batcher configuration
     * @param queue the internal queue for pending requests
     */
    public DefaultBatcherDiagnostics(boolean closed, BatcherConfig config, BlockingQueue<PendingRequest<T>> queue) {
        this.closed = closed;
        this.config = config;
        this.queue = queue;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public int getCurrentBatchSize() {
        return config.getBatchSize();
    }

    @Override
    public Duration getCurrentLingerTime() {
        return config.getLingerTime();
    }

    @Override
    public int getQueueDepth() {
        return queue.size();
    }
}

