package com.holub.life.plugin.view2d;

import com.holub.life.api.CellState;

import java.awt.Color;

/** Strategy for one cell type: decides the color from that cell's own state. */
@FunctionalInterface
public interface CellPainter {
    Color colorOf(CellState state);
}
