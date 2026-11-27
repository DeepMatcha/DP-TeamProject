package com.holub.life.plugin.obstacle;

import com.holub.life.api.CellState;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;

import java.util.Map;

/**
 * Conway's rule on a board with walls. A wall stays where it is, counts as a dead neighbor,
 * and nothing is ever born on it. This is a complete evolution rule that runs
 * <em>instead of</em> the host's basic Conway stage (the plugin declares the takeover):
 * a rule that does not know walls may erase them.
 * <ul>
 * <li>wall: unchanged;</li>
 * <li>living cell (of any type): survives with 2 or 3 living neighbors, otherwise becomes the host's empty cell;</li>
 * <li>host's empty cell: becomes the host's alive cell with exactly 3 living neighbors;</li>
 * <li>any other type that is not alive: unchanged.</li>
 * </ul>
 */
public final class ObstacleConwayRule extends LifecycleManager {
    private final CellTypeLookup types;

    public ObstacleConwayRule(CellTypeLookup types) {
        super(1);
        this.types = types;
    }

    @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
        CellState current = neighbors.center().state();
        if (current.type() == ObstacleType.INSTANCE) return current;

        long living = neighbors.cells().stream()
                .filter(sample -> sample.state().type().isAlive(sample.state())).count();
        if (current.type().isAlive(current))
            return living == 2 || living == 3 ? current : new CellState(CoreCellTypes.empty(types), Map.of());
        if (current.type() == CoreCellTypes.empty(types) && living == 3)
            return new CellState(CoreCellTypes.alive(types), Map.of());
        return current;
    }
}
