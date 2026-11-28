package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;

import java.util.Map;

/**
 * Food appears in bursts and is eaten by living cells, without any cell changing another.
 * Each cell reads the same snapshot and returns only its own next state:
 * <ul>
 * <li>an empty cell occasionally becomes a burst seed;</li>
 * <li>a burst seed removes itself on the next tick;</li>
 * <li>an empty cell near a seed becomes food with a chance that falls with the distance;</li>
 * <li>food next to a living cell is eaten and becomes empty.</li>
 * </ul>
 * Randomness comes only from the host-provided generator of the evaluation.
 */
public final class FoodRule extends LifecycleManager {
    public static final double DEFAULT_SEED_CHANCE = 0.0005;
    public static final double DEFAULT_BURST_CHANCE = 0.8;
    public static final int DEFAULT_BURST_RADIUS = 2;

    private final CellTypeLookup types;
    private final double seedChance;
    private final double burstChance;

    public FoodRule(CellTypeLookup types, double seedChance, double burstChance, int burstRadius) {
        super(burstRadius);
        if (burstRadius < 1) throw new IllegalArgumentException("burstRadius must be at least 1");
        this.types = types;
        this.seedChance = seedChance;
        this.burstChance = burstChance;
    }

    @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
        CellState current = neighbors.center().state();
        CellType type = current.type();
        CellType empty = CoreCellTypes.empty(types);

        if (type == BurstSeedType.INSTANCE) return new CellState(empty, Map.of());
        if (type == FoodType.INSTANCE)
            return Neighbors.touches(neighbors, CoreCellTypes.alive(types)) ? new CellState(empty, Map.of()) : current;
        if (type != empty) return current;

        int seedDistance = Neighbors.nearest(neighbors, BurstSeedType.INSTANCE, distance());
        if (seedDistance > 0)
            return context.random().nextDouble() < burstChance / seedDistance ? FoodType.STATE : current;
        return context.random().nextDouble() < seedChance ? BurstSeedType.STATE : current;
    }
}
