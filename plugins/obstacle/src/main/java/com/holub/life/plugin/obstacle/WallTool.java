package com.holub.life.plugin.obstacle;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.ToolInput;

import java.util.List;
import java.util.Map;

/** Click tool: builds a wall on the clicked cell, or removes the wall that is there. */
public final class WallTool implements InputTool {
    public static final String ID = "obstacle:toggle-wall";

    private final CellTypeLookup types;

    public WallTool(CellTypeLookup types) { this.types = types; }

    @Override public String id() { return ID; }

    /** The patch assigns a value to the canonical position; a click outside the board changes nothing. */
    @Override public BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor) {
        return accessor.access(input.anchor())
                .map(target -> {
                    CellState value = target.state().type() == ObstacleType.INSTANCE
                            ? new CellState(CoreCellTypes.empty(types), Map.of())
                            : ObstacleType.STATE;
                    return new BoardPatch(List.of(new CellEdit(target.canonicalPos(), value)));
                })
                .orElseGet(() -> new BoardPatch(List.of()));
    }
}
