package com.holub.life.host;

import com.holub.life.api.PlaybackState;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Playback state transitions, observed through the executor's queue instead of waiting for ticks. */
class PlaybackTimerTest {
    private static final int SLOW = 60_000;

    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final PlaybackTimer timer = new PlaybackTimer(executor, () -> {});

    PlaybackTimerTest() {
        executor.setRemoveOnCancelPolicy(true);
    }

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void repeatedStartsScheduleOneTimer() {
        timer.setInterval(SLOW);
        timer.start();
        timer.start();

        assertEquals(1, executor.getQueue().size());
        assertEquals(new PlaybackState(true, SLOW), timer.state());
    }

    @Test
    void changingSpeedNeitherStartsNorStopsPlayback() {
        timer.setInterval(SLOW);
        assertEquals(new PlaybackState(false, SLOW), timer.state());
        assertEquals(0, executor.getQueue().size());

        timer.start();
        timer.setInterval(SLOW + 1);
        assertEquals(new PlaybackState(true, SLOW + 1), timer.state());
        assertEquals(1, executor.getQueue().size(), "the running timer is replaced, not duplicated");
    }

    @Test
    void stopCancelsTheTimer() {
        timer.setInterval(SLOW);
        timer.start();
        timer.stop();

        assertEquals(0, executor.getQueue().size());
        assertEquals(new PlaybackState(false, SLOW), timer.state());
    }

    @Test
    void intervalMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> timer.setInterval(0));
        assertEquals(PlaybackTimer.DEFAULT_INTERVAL_MILLIS, timer.state().millisecondsBetweenTicks());
    }
}
