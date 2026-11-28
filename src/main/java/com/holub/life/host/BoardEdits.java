package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;
import com.holub.life.api.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

/** Pure board arithmetic for direct edits: no Model, no thread, no plan. */
final class BoardEdits {
    private BoardEdits() {}

    /**
     * Applies the patch as ordered assignments; the last write to a position wins.
     * Each final value passes the validator before anything changes.
     * Returns {@code current} itself when no cell changes, so callers can skip a commit.
     */
    static BoardState applied(BoardState current, BoardPatch patch, Consumer<CellState> validator) {
        var lastEdits = new LinkedHashMap<GridPos, CellState>();
        for (CellEdit edit : patch.edits()) {
            lastEdits.put(edit.position(), edit.value());
        }
        var cells = new ArrayList<>(current.cells());
        boolean changed = false;
        for (var edit : lastEdits.entrySet()) {
            GridPos position = edit.getKey();
            CellState previous = current.cellAt(position); // Check canonical coordinates without wrapping.
            CellState state = edit.getValue();
            validator.accept(state);
            if (previous.type() != state.type() || !previous.properties().equals(state.properties())) {
                cells.set(position.y() * current.size().width() + position.x(), state);
                changed = true;
            }
        }
        return changed ? new BoardState(current.size(), current.generation(), current.seed(), cells) : current;
    }

    /** Every cell becomes {@code empty}, then the pattern's cells (if any) are placed from the origin. */
    static BoardPatch replacement(BoardState current, Pattern pattern, CellState empty) {
        var edits = new ArrayList<CellEdit>();
        for (int y = 0; y < current.size().height(); y++) {
            for (int x = 0; x < current.size().width(); x++) {
                edits.add(new CellEdit(new GridPos(x, y), empty));
            }
        }
        if (pattern != null) {
            edits.addAll(pattern.cells());
        }
        return new BoardPatch(edits);
    }
}
