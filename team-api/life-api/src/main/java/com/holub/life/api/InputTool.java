package com.holub.life.api;
/** UI/TUI input is transformed into the SAME bulk-edit command. */
public interface InputTool {
    String id();
    BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor);
}
