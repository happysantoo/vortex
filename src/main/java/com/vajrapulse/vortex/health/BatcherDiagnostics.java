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
     * Returns the current batch size used for forming batches.
     *
     * <p>This reflects dynamic updates performed via {@link MicroBatcher#updateBatchSize(int)}.
     *
     * @return current batch size
     */
    int getCurrentBatchSize();

    /**
     * Returns the current linger time used for forming batches.
     *
     * <p>This reflects dynamic updates performed via {@link MicroBatcher#updateLingerTime(Duration)}.
     *
     * @return current linger time
     */
    Duration getCurrentLingerTime();

    /**
     * Returns the current queue depth (number of pending requests).
     *
     * @return current queue depth
     */
    int getQueueDepth();
}


