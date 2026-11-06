package com.holub.life.api;
/** Fixed radius per manager instance; Integer.MAX_VALUE means all other board cells. */
public abstract class LifecycleManager {
    private final int distance;
    protected LifecycleManager(int distance) {
        if (distance < 0) throw new IllegalArgumentException("Negative distance");
        this.distance = distance;
    }
    public final int distance() { return distance; }
    public abstract CellState figureNextState(NeighborBoard neighbors, StepContext context);
}
