package com.holub.life.api;
import java.util.*;
/** Session-selected accessor, global plugin order, and serializable options. */
public record SimulationPlan(
        String accessorId, List<PluginPriority> plugins,
        Map<String, Map<String,String>> pluginOptions) {
    public SimulationPlan {
        Objects.requireNonNull(accessorId);
        plugins = List.copyOf(Objects.requireNonNull(plugins));
        Objects.requireNonNull(pluginOptions);
        Map<String,Map<String,String>> copy = new LinkedHashMap<>();
        pluginOptions.forEach((k,v) -> copy.put(Objects.requireNonNull(k), Map.copyOf(v)));
        pluginOptions = Map.copyOf(copy);
    }
}
