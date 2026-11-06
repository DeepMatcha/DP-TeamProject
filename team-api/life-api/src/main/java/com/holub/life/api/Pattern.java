package com.holub.life.api;
import java.util.List;
import java.util.Objects;
/** Brush/preset: relative coordinates, with no generation/session semantics. */
public record Pattern(BoardSize size, List<CellEdit> cells) {
    public Pattern {
        Objects.requireNonNull(size);
        cells = List.copyOf(Objects.requireNonNull(cells));
    }
}
