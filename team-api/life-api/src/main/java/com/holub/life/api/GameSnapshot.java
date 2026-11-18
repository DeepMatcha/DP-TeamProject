package com.holub.life.api;
import java.util.Map;
import java.util.Objects;
/**
 * Restartable simulation: board (seed/generation), ordered plan, plugin versions.
 * Timer running/speed and View data are not part of this snapshot.
 * Host reconstructs deterministic random progress from these simulation inputs.
 */
public record GameSnapshot(
        BoardState board, SimulationPlan plan, Map<String,String> pluginVersions) {
    public GameSnapshot {
        Objects.requireNonNull(board);
        Objects.requireNonNull(plan);
        pluginVersions = Map.copyOf(Objects.requireNonNull(pluginVersions));
    }
}
