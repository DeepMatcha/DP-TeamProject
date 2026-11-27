package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;

import java.util.Map;

/** Nutrient lying on the board. Not alive, so it never counts as a living neighbor. */
public final class FoodType extends CellType {
    public static final FoodType INSTANCE = new FoodType();
    public static final CellState STATE = new CellState(INSTANCE, Map.of());

    private FoodType() { super("foodaging:food"); }

    @Override public boolean isAlive(CellState state) { return false; }
}
