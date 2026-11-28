package com.holub.life.host;

import com.holub.life.api.BoardState;
import com.holub.life.api.GridPos;
import com.holub.ui.Colors;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.util.Optional;
import javax.swing.JPanel;

/** Paints the committed board in the original Holub colors; layout comes from {@link BoardGeometry}. */
final class BoardPanel extends JPanel {
    private BoardState state;

    BoardPanel(BoardState initial) {
        state = initial;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(512, 512));
    }

    void display(BoardState next) {
        state = next;
        repaint();
    }

    Optional<GridPos> positionAt(Point point) {
        return geometry().positionAt(point);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        BoardGeometry geometry = geometry();
        int width = state.size().width();
        int height = state.size().height();
        for (int y = 0; y < height; y++) {
            int top = geometry.rowEdge(y);
            int bottom = geometry.rowEdge(y + 1);
            for (int x = 0; x < width; x++) {
                int left = geometry.columnEdge(x);
                int right = geometry.columnEdge(x + 1);
                var cell = state.cells().get(y * width + x);
                graphics.setColor(cell.type().isAlive(cell) ? Color.RED : Colors.LIGHT_YELLOW);
                graphics.fillRect(left, top, right - left, bottom - top);
                graphics.setColor(Colors.DARK_YELLOW);
                graphics.drawLine(left, top, right, top);
                graphics.drawLine(left, top, left, bottom);
            }
        }
    }

    private BoardGeometry geometry() {
        return new BoardGeometry(state.size(), getWidth(), getHeight());
    }
}
