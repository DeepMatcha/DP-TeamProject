package com.holub.life.api;
import java.util.Objects;
/** A value assignment to a canonical 2D board position, not a toggle. */
public record CellEdit(GridPos position, CellState value) {
    public CellEdit { Objects.requireNonNull(position); Objects.requireNonNull(value); }
}
