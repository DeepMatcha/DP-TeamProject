package com.holub.life.plugin.toroidal;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.GridPos;

import java.util.Optional;

/**
 * Wraps positions around both edges, so the board has no border: leaving on the right
 * re-enters on the left, and the same for top and bottom. Every position resolves to a cell.
 */
public final class ToroidalAccessor implements AccessorFactory<GridPos> {
    public static final String ID = "toroidal:wrap";

    @Override public String id() { return ID; }
    @Override public Class<GridPos> coordinateType() { return GridPos.class; }

    @Override public Accessor<GridPos> bind(BoardState snapshot) {
        int width = snapshot.size().width(), height = snapshot.size().height();
        return position -> {
            GridPos canonical = new GridPos(Math.floorMod(position.x(), width), Math.floorMod(position.y(), height));
            return Optional.of(new CellSample(canonical, snapshot.cellAt(canonical)));
        };
    }
}
