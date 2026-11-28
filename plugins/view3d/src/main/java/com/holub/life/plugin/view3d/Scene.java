package com.holub.life.plugin.view3d;

import com.holub.life.api.BoardSize;

import java.util.List;

/** Everything the renderer needs for one committed board. Cells without a pillar are bare floor. */
public record Scene(BoardSize size, boolean torus, List<Placed> pillars, String caption) {
    public record Placed(int x, int y, Pillar pillar) {}

    public Scene {
        pillars = List.copyOf(pillars);
    }
}
