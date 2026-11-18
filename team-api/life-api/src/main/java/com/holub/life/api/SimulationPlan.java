package com.holub.life.api;
import java.util.*;
/** Ordered plugin selection; one active accessor. Lists and options are immutable. */
public record SimulationPlan(
        String accessorId, List<PluginSelection> plugins,
        Map<String, Map<String,String>> pluginOptions) {
    public SimulationPlan {
        Objects.requireNonNull(accessorId);
        if (accessorId.isBlank()) throw new IllegalArgumentException("Blank accessor id");
        plugins = List.copyOf(Objects.requireNonNull(plugins));
        Set<String> ids = new HashSet<>();
        for (PluginSelection plugin : plugins) {
            if (!ids.add(plugin.pluginId()))
                throw new IllegalArgumentException("Duplicate plugin: " + plugin.pluginId());
        }
        Objects.requireNonNull(pluginOptions);
        Map<String,Map<String,String>> copy = new LinkedHashMap<>();
        pluginOptions.forEach((key,value) ->
                copy.put(Objects.requireNonNull(key), Map.copyOf(value)));
        pluginOptions = Map.copyOf(copy);
    }
}
