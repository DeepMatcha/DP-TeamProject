package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellState;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Living cells grow one tick older each tick and die of old age, unless food next to them
 * resets their age. The age is a per-coordinate property of the host's alive cells.
 * Every other type is returned unchanged.
 */
public final class AgingRule extends LifecycleManager {
    public static final String AGE = "foodaging:age";
    public static final int DEFAULT_MAX_AGE = 20;

    private final CellTypeLookup types;
    private final int maxAge;

    public AgingRule(CellTypeLookup types, int maxAge) {
        super(1);
        if (maxAge < 1) throw new IllegalArgumentException("maxAge must be at least 1");
        this.types = types;
        this.maxAge = maxAge;
    }

    public int maxAge() { return maxAge; }

    /** A cell that has never been aged has age 0. */
    public static int ageOf(CellState state) {
        return Integer.parseInt(state.properties().getOrDefault(AGE, "0"));
    }

    @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
        CellState current = neighbors.center().state();
        if (current.type() != CoreCellTypes.alive(types)) return current;
        if (Neighbors.touches(neighbors, FoodType.INSTANCE)) return withAge(current, 0);
        int age = ageOf(current) + 1;
        return age > maxAge ? new CellState(CoreCellTypes.empty(types), Map.of()) : withAge(current, age);
    }

    private static CellState withAge(CellState state, int age) {
        Map<String, String> properties = new HashMap<>(state.properties());
        properties.put(AGE, Integer.toString(age));
        return new CellState(state.type(), properties);
    }
}
