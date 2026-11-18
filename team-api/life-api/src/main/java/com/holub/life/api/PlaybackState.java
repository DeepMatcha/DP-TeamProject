package com.holub.life.api;
/** Host scheduling state; independent of the immutable simulation snapshot. */
public record PlaybackState(boolean running, int millisecondsBetweenTicks) {
    public PlaybackState {
        if (millisecondsBetweenTicks <= 0)
            throw new IllegalArgumentException("Tick interval must be positive");
    }
}
