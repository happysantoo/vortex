package com.vajrapulse.vortex.internal;

import com.vajrapulse.vortex.BatcherConfig;
import com.vajrapulse.vortex.health.BatcherDiagnostics;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

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
    private final Supplier<Boolean> closedSupplier;
    private final BatcherConfig config;
    private final BlockingQueue<PendingRequest<T>> queue;

    /**
     * Creates a new DefaultBatcherDiagnostics instance.
     *
     * @param closedSupplier supplier to check if the batcher is closed
     * @param config the batcher configuration
     * @param queue the internal queue for pending requests
     */
    public DefaultBatcherDiagnostics(Supplier<Boolean> closedSupplier, BatcherConfig config, BlockingQueue<PendingRequest<T>> queue) {
        this.closedSupplier = closedSupplier;
        this.config = config;
        this.queue = queue;
    }

    @Override
    public boolean isClosed() {
        return closedSupplier.get();
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

