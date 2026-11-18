package com.holub.life.api;
/**
 * Fixed radius per instance; Integer.MAX_VALUE means all other canonical cells.
 * The host invokes a registered rule on every cell, without automatic type filtering.
 * Each rule owns its treatment of every type, including unknown extension types.
 */
public abstract class LifecycleManager {
    private final int distance;
    protected LifecycleManager(int distance) {
        if (distance < 0) throw new IllegalArgumentException("Negative distance");
        this.distance = distance;
    }
    public final int distance() { return distance; }
    /** Read the current stage snapshot and return only this center's next state. */
    public abstract CellState figureNextState(NeighborBoard neighbors, StepContext context);
}
