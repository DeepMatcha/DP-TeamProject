package com.holub.life.plugin.obstacle;

import com.holub.life.plugin.view2d.CellPainters;

import java.awt.Color;

/** Kept apart from {@link ObstaclePlugin} so that the plugin class loads without the view library. */
final class ObstaclePainters {
    static final Color WALL = new Color(0x505058);

    private ObstaclePainters() {}

    static void register() { CellPainters.register(ObstacleType.INSTANCE, state -> WALL); }
}
