package com.holub.life.plugin.view3d;

import com.holub.life.api.CellState;
import com.holub.life.plugin.obstacle.ObstacleType;

import java.util.Optional;

/** Walls as tall grey blocks. Loaded only when the obstacle plugin is installed. */
final class ObstacleStyle implements CellStyle {
    static final Pillar WALL = Pillar.of(1.6f, 0x6A6A78, Pillar.Glow.NONE);

    @Override public Optional<Pillar> pillarOf(CellState state) {
        return state.type() == ObstacleType.INSTANCE ? Optional.of(WALL) : Optional.empty();
    }
}
