package com.holub.life.api;
/** For effects that coordinate multiple target cells (food spawn, etc.). */
public interface BoardEffect {
    BoardPatch apply(BoardState source, Accessor<GridPos> accessor, StepContext context);
}
