package com.holub.life.plugin.view3d;

import com.holub.life.api.CellState;

import java.util.Optional;

/** How one plugin shows its cells in 3D. Empty means "not my cell", and the next style is asked. */
@FunctionalInterface
public interface CellStyle {
    Optional<Pillar> pillarOf(CellState state);
}
