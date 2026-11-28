package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;
import com.holub.life.api.NeighborBoard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only view of a rule's stage input around one canonical cell. Coordinates are resolved
 * by the plan's {@code Accessor} (Strategy), so the same rule runs on a bounded or wrapped board.
 */
final class SnapshotNeighborBoard implements NeighborBoard {
    private static final Comparator<CellSample> ROW_MAJOR =
        Comparator.comparingInt((CellSample sample) -> sample.canonicalPos().y())
            .thenComparingInt(sample -> sample.canonicalPos().x());

    private final BoardState source;
    private final Accessor<GridPos> accessor;
    private final CellSample center;
    private final int distance;
    private final boolean bounded;

    SnapshotNeighborBoard(
            BoardState source, Accessor<GridPos> accessor, GridPos center, int distance, boolean bounded) {
        this.source = source;
        this.accessor = accessor;
        this.center = new CellSample(center, source.cellAt(center));
        this.distance = distance;
        this.bounded = bounded;
    }

    @Override
    public CellSample center() {
        return center;
    }

    @Override
    public int distance() {
        return distance;
    }

    @Override
    public Optional<CellSample> atOffset(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return Optional.of(center);
        }
        if (distance != Integer.MAX_VALUE
                && (Math.abs((long) dx) > distance || Math.abs((long) dy) > distance)) {
            return Optional.empty();
        }
        return readOffset(dx, dy);
    }

    @Override
    public List<CellSample> cells() {
        if (distance == 0 || source.cells().size() == 1) {
            return List.of();
        }
        if (distance == Integer.MAX_VALUE || bounded) {
            return canonicalCells();
        }
        return mappedCells();
    }

    private List<CellSample> canonicalCells() {
        GridPos position = center.canonicalPos();
        int minX = (int) Math.max(0L, (long) position.x() - distance);
        int minY = (int) Math.max(0L, (long) position.y() - distance);
        int maxX = (int) Math.min(source.size().width() - 1L, (long) position.x() + distance);
        int maxY = (int) Math.min(source.size().height() - 1L, (long) position.y() + distance);
        List<CellSample> cells = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (x != position.x() || y != position.y()) {
                    GridPos neighbor = new GridPos(x, y);
                    cells.add(new CellSample(neighbor, source.cellAt(neighbor)));
                }
            }
        }
        return List.copyOf(cells);
    }

    private List<CellSample> mappedCells() {
        Map<GridPos, CellSample> cells = new HashMap<>();
        int maximumNeighbors = source.cells().size() - 1;
        // Visit the closest rings first. A wrapping accessor can cover the board
        // long before reaching the requested radius.
        for (int radius = 1; radius <= distance; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                addMappedCell(cells, dx, -radius);
                addMappedCell(cells, dx, radius);
                if (cells.size() == maximumNeighbors) {
                    return sortedCells(cells);
                }
            }
            for (int dy = 1 - radius; dy < radius; dy++) {
                addMappedCell(cells, -radius, dy);
                addMappedCell(cells, radius, dy);
                if (cells.size() == maximumNeighbors) {
                    return sortedCells(cells);
                }
            }
        }
        return sortedCells(cells);
    }

    private void addMappedCell(Map<GridPos, CellSample> cells, int dx, int dy) {
        readOffset(dx, dy).ifPresent(sample -> {
            if (!center.canonicalPos().equals(sample.canonicalPos())) {
                cells.putIfAbsent(sample.canonicalPos(), sample);
            }
        });
    }

    private static List<CellSample> sortedCells(Map<GridPos, CellSample> cells) {
        return cells.values().stream().sorted(ROW_MAJOR).toList();
    }

    private Optional<CellSample> readOffset(int dx, int dy) {
        long x = (long) center.canonicalPos().x() + dx;
        long y = (long) center.canonicalPos().y() + dy;
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        GridPos position = new GridPos((int) x, (int) y);
        return Objects.requireNonNull(accessor.access(position), "Accessor result")
            .map(this::validateSample);
    }

    private CellSample validateSample(CellSample sample) {
        GridPos position = Objects.requireNonNull(sample.canonicalPos(), "Canonical position");
        if (!BoundedAccessor.contains(source.size(), position)) {
            throw new IllegalArgumentException("Accessor returned a noncanonical position: " + position);
        }
        CellState expected = source.cellAt(position);
        CellState actual = Objects.requireNonNull(sample.state(), "Accessor cell state");
        if (actual != expected
                && (actual.type() != expected.type() || !actual.properties().equals(expected.properties()))) {
            throw new IllegalArgumentException("Accessor returned state outside its stage snapshot: " + position);
        }
        return sample;
    }
}
