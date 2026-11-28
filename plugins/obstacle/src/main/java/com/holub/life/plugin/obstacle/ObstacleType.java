package com.holub.life.plugin.obstacle;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;

import java.util.Map;

/** A wall: never alive, never changed by {@link ObstacleConwayRule}. */
public final class ObstacleType extends CellType {
    public static final ObstacleType INSTANCE = new ObstacleType();
    public static final CellState STATE = new CellState(INSTANCE, Map.of());

    private ObstacleType() { super("obstacle:wall"); }

    @Override public boolean isAlive(CellState state) { return false; }
}
