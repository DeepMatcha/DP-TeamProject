package com.holub.life.host;

import com.holub.life.api.CellState;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;

import java.util.Map;

/**
 * Conway B3/S23 for the host's core cells. Extension types control their own transitions.
 * A rule is a Strategy the engine applies to every cell; the engine owns iteration and commit.
 */
public final class CoreConwayRule extends LifecycleManager {
    private static final CellState EMPTY_STATE = new CellState(CoreCells.EMPTY, Map.of());
    private static final CellState ALIVE_STATE = new CellState(CoreCells.ALIVE, Map.of());

    public CoreConwayRule() {
        super(1);
    }

    @Override
    public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
        CellState center = neighbors.center().state();
        if (center.type() != CoreCells.ALIVE && center.type() != CoreCells.EMPTY) {
            return center;
        }

        int livingNeighbors = 0;
        for (var sample : neighbors.cells()) {
            CellState state = sample.state();
            if (state.type().isAlive(state)) {
                livingNeighbors++;
            }
        }

        if (center.type() == CoreCells.ALIVE) {
            return livingNeighbors == 2 || livingNeighbors == 3 ? center : EMPTY_STATE;
        }
        return livingNeighbors == 3 ? ALIVE_STATE : center;
    }
}
