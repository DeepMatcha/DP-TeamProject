package com.holub.life.api;
import java.util.Objects;
/** One GLOBAL UI priority, reused at every API point where this plugin participates. */
public record PluginPriority(String pluginId, int priority, boolean enabled) {
    public PluginPriority { Objects.requireNonNull(pluginId); }
}
