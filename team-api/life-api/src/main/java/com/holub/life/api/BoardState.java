package com.holub.life.api;
import java.util.List;
import java.util.Objects;
/** Immutable, row-major 2D board; direct canonical access does not wrap. */
public record BoardState(BoardSize size, long generation, long seed, List<CellState> cells) {
    public BoardState {
        Objects.requireNonNull(size);
        cells = List.copyOf(Objects.requireNonNull(cells));
        if (cells.size() != size.area()) throw new IllegalArgumentException("Incorrect cell count");
    }
    public CellState cellAt(GridPos pos) {
        if (pos.x()<0 || pos.y()<0 || pos.x()>=size.width() || pos.y()>=size.height())
            throw new IndexOutOfBoundsException(pos.toString());
        return cells.get(pos.y() * size.width() + pos.x());
    }
}
