package com.holub.life.api;
import java.util.Optional;
/** Read-only. A given accessor is bound to one source BoardState snapshot. */
public interface Accessor<T extends Coordinate> {
    Optional<CellSample> access(T position);
}
