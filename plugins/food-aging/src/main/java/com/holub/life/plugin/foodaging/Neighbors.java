package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellType;
import com.holub.life.api.NeighborBoard;

/** Offset-based neighbor queries; offsets keep their distance meaning even when the accessor wraps. */
final class Neighbors {
    private Neighbors() {}

    /** Smallest Chebyshev offset distance (1..radius) to a cell of the type, or 0 if there is none. */
    static int nearest(NeighborBoard neighbors, CellType type, int radius) {
        for (int distance = 1; distance <= radius; distance++)
            for (int dy = -distance; dy <= distance; dy++)
                for (int dx = -distance; dx <= distance; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != distance) continue;
                    if (neighbors.atOffset(dx, dy).filter(sample -> sample.state().type() == type).isPresent())
                        return distance;
                }
        return 0;
    }

    static boolean touches(NeighborBoard neighbors, CellType type) {
        return nearest(neighbors, type, 1) == 1;
    }
}
