package com.holub.life.api;
import java.util.Objects;
/** Position in SimulationPlan.plugins(), not a numeric priority, defines order. */
public record PluginSelection(String pluginId, boolean enabled) {
    public PluginSelection {
        Objects.requireNonNull(pluginId);
        if (pluginId.isBlank()) throw new IllegalArgumentException("Blank plugin id");
    }
}
