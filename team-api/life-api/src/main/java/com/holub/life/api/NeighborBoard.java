package com.holub.life.api;
import java.util.List;
import java.util.Optional;
/** Read-only spatial window over one stage snapshot, not an optimization block. */
public interface NeighborBoard {
    CellSample center();
    int distance();
    /** (0,0) returns center(); out-of-range is empty; other offsets use the accessor. */
    Optional<CellSample> atOffset(int dx, int dy);
    /** Distinct canonical neighbors, excluding center, sorted by (y,x); immutable. */
    List<CellSample> cells();
}
