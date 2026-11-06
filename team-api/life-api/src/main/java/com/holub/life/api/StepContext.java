package com.holub.life.api;
import java.util.Objects;
/** Seed and generation are supplied by the current source snapshot. */
public record StepContext(long generation, long seed, String pluginId) {
    public StepContext { Objects.requireNonNull(pluginId); }
}
