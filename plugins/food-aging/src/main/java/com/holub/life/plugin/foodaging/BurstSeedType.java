package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;

import java.util.Map;

/**
 * A cell that is about to burst. It lasts exactly one tick: on the next tick it removes
 * itself, and the empty cells around it decide for themselves whether to become food.
 */
public final class BurstSeedType extends CellType {
    public static final BurstSeedType INSTANCE = new BurstSeedType();
    public static final CellState STATE = new CellState(INSTANCE, Map.of());

    private BurstSeedType() { super("foodaging:burst-seed"); }

    @Override public boolean isAlive(CellState state) { return false; }
}
