package com.holub.life.testkit;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.GridPos;
import com.holub.life.api.NeighborBoard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Neighbor window following API-CONTRACT section 4, for plugin tests without a real host. */
public final class FakeNeighborBoard implements NeighborBoard {
    private static final Comparator<GridPos> ROW_MAJOR =
            Comparator.comparingInt(GridPos::y).thenComparingInt(GridPos::x);

    private final BoardState board;
    private final Accessor<GridPos> accessor;
    private final GridPos center;
    private final int distance;

    public FakeNeighborBoard(BoardState board, Accessor<GridPos> accessor, GridPos center, int distance) {
        this.board = board;
        this.accessor = accessor;
        this.center = center;
        this.distance = distance;
    }

    @Override public CellSample center() { return new CellSample(center, board.cellAt(center)); }
    @Override public int distance() { return distance; }

    @Override public Optional<CellSample> atOffset(int dx, int dy) {
        if (dx == 0 && dy == 0) return Optional.of(center());
        if (Math.abs(dx) > distance || Math.abs(dy) > distance) return Optional.empty();
        return accessor.access(new GridPos(center.x() + dx, center.y() + dy));
    }

    @Override public List<CellSample> cells() {
        Map<GridPos, CellSample> found = new TreeMap<>(ROW_MAJOR);
        if (distance == Integer.MAX_VALUE) {
            for (int y = 0; y < board.size().height(); y++)
                for (int x = 0; x < board.size().width(); x++) {
                    GridPos pos = new GridPos(x, y);
                    found.put(pos, new CellSample(pos, board.cellAt(pos)));
                }
        } else {
            for (int dy = -distance; dy <= distance; dy++)
                for (int dx = -distance; dx <= distance; dx++)
                    atOffset(dx, dy).ifPresent(sample -> found.put(sample.canonicalPos(), sample));
        }
        found.remove(center);
        return List.copyOf(new ArrayList<>(found.values()));
    }
}
