package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.plugin.view2d.CellPainters;

import java.awt.Color;

/**
 * Colors of this plugin's cells in the 2D view. Kept apart from {@link FoodAgingPlugin}
 * so that the plugin class itself loads even when the view library is missing.
 */
final class FoodAgingPainters {
    static final Color FOOD = new Color(0x2E9E3A);
    static final Color BURST_SEED = new Color(0xFF9A00);
    static final Color YOUNG = new Color(0xFF4040);
    static final Color OLD = new Color(0x4A1010);

    private FoodAgingPainters() {}

    static void register(CellType alive, int maxAge) {
        CellPainters.register(FoodType.INSTANCE, state -> FOOD);
        CellPainters.register(BurstSeedType.INSTANCE, state -> BURST_SEED);
        CellPainters.register(alive, state -> ageColor(state, maxAge));
    }

    /** Fades from bright red at age 0 to dark brown at the maximum age. */
    static Color ageColor(CellState state, int maxAge) {
        double old = Math.min(1.0, (double) AgingRule.ageOf(state) / maxAge);
        return new Color(mix(YOUNG.getRed(), OLD.getRed(), old),
                mix(YOUNG.getGreen(), OLD.getGreen(), old), mix(YOUNG.getBlue(), OLD.getBlue(), old));
    }

    private static int mix(int from, int to, double ratio) { return (int) Math.round(from + (to - from) * ratio); }
}
