package com.holub.life.api;
import java.util.List;
import java.util.Objects;
/** Ordered edits: after canonicalization, last write wins; then validate and commit atomically. */
public record BoardPatch(List<CellEdit> edits) {
    public BoardPatch { edits = List.copyOf(Objects.requireNonNull(edits)); }
}
