package com.vajrapulse.vortex.health;

import com.vajrapulse.vortex.MicroBatcher;
import java.time.Duration;

/**
 * Provides a lightweight, read-only view of the current {@link MicroBatcher} state.
 *
 * <p>This interface is intended for diagnostics, health checks, and operational
 * dashboards. It intentionally exposes only a small set of fields that are safe
 * to read concurrently and do not mutate internal state.
 *
 * @since 0.0.3
 */
public interface BatcherDiagnostics {

    /**
     * Returns whether the batcher has been closed.
     *
     * @return {@code true} if {@link MicroBatcher#close()} has been called, {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Returns the batch size used for forming batches.
     *
     * <p>This returns the batch size from the configuration.
     *
     * @return batch size from configuration
     */
    int getCurrentBatchSize();

    /**
     * Returns the linger time used for forming batches.
     *
     * <p>This returns the linger time from the configuration.
     *
     * @return linger time from configuration
     */
    Duration getCurrentLingerTime();

    /**
     * Returns the current queue depth (number of pending requests).
     *
     * @return current queue depth
     */
    int getQueueDepth();
}


