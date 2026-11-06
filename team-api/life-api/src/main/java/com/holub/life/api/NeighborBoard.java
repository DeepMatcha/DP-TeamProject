package com.holub.life.api;
import java.util.List;
import java.util.Optional;
/** Read-only board-shaped window. Does not expose internal 8x8 optimization partitions. */
public interface NeighborBoard {
    CellSample center();
    int distance();
    Optional<CellSample> atOffset(int dx, int dy);
    /** Distinct canonical neighbors, excluding center, sorted by (y,x). */
    List<CellSample> cells();
}
