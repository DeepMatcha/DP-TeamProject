package com.holub.life.host;

import com.holub.life.api.PlaybackState;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Automatic playback: repeats one tick at a fixed delay on the host's operation thread.
 * Not thread-safe by itself; the owner calls it only from that thread, so a tick and a
 * playback change never interleave.
 */
final class PlaybackTimer {
    static final int DEFAULT_INTERVAL_MILLIS = 150;

    private final ScheduledExecutorService operations;
    private final Runnable tick;
    private int intervalMillis = DEFAULT_INTERVAL_MILLIS;
    private ScheduledFuture<?> timer;

    PlaybackTimer(ScheduledExecutorService operations, Runnable tick) {
        this.operations = Objects.requireNonNull(operations);
        this.tick = Objects.requireNonNull(tick);
    }

    /** Starting while running keeps the existing timer, so no extra ticks are scheduled. */
    void start() {
        if (timer == null) {
            timer = operations.scheduleWithFixedDelay(tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    void stop() {
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
    }

    /** Changing speed neither starts nor stops playback; a running timer restarts at the new delay. */
    void setInterval(int milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        if (intervalMillis != milliseconds) {
            intervalMillis = milliseconds;
            if (timer != null) {
                stop();
                start();
            }
        }
    }

    PlaybackState state() {
        return new PlaybackState(timer != null, intervalMillis);
    }
}
