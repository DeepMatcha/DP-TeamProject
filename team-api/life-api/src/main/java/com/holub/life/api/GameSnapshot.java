package com.holub.life.api;
import java.util.Map;
import java.util.Objects;
/** Complete restart: board + active accessor/plugin config + installed versions. */
public record GameSnapshot(
        BoardState board, SimulationPlan plan, Map<String,String> pluginVersions) {
    public GameSnapshot {
        Objects.requireNonNull(board);
        Objects.requireNonNull(plan);
        pluginVersions = Map.copyOf(Objects.requireNonNull(pluginVersions));
    }
}
