package com.holub.life.plugin.view3d;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.plugin.foodaging.AgingRule;
import com.holub.life.plugin.foodaging.BurstSeedType;
import com.holub.life.plugin.foodaging.FoodType;

import java.util.Optional;

/**
 * Food glows, a burst seed pulses, and a living cell grows taller and darker with age.
 * Loaded only when the food and aging plugin is installed.
 */
final class FoodAgingStyle implements CellStyle {
    static final Pillar FOOD = Pillar.of(0.3f, 0x30E050, Pillar.Glow.STEADY);
    static final Pillar BURST_SEED = Pillar.of(0.7f, 0xFFA020, Pillar.Glow.PULSE);
    static final float YOUNG_HEIGHT = 0.5f, OLD_HEIGHT = 3.0f;
    private static final int YOUNG = 0xFF5040, OLD = 0x702070;

    private final CellType alive;

    FoodAgingStyle(CellType alive) { this.alive = alive; }

    @Override public Optional<Pillar> pillarOf(CellState state) {
        if (state.type() == FoodType.INSTANCE) return Optional.of(FOOD);
        if (state.type() == BurstSeedType.INSTANCE) return Optional.of(BURST_SEED);
        if (state.type() != alive) return Optional.empty();
        float old = Math.min(1f, (float) AgingRule.ageOf(state) / AgingRule.DEFAULT_MAX_AGE);
        return Optional.of(new Pillar(YOUNG_HEIGHT + (OLD_HEIGHT - YOUNG_HEIGHT) * old,
                mix(YOUNG >> 16 & 0xFF, OLD >> 16 & 0xFF, old), mix(YOUNG >> 8 & 0xFF, OLD >> 8 & 0xFF, old),
                mix(YOUNG & 0xFF, OLD & 0xFF, old), Pillar.Glow.NONE));
    }

    private static float mix(int from, int to, float ratio) { return (from + (to - from) * ratio) / 255f; }
}
