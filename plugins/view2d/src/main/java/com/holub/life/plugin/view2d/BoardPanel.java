package com.holub.life.plugin.view2d;

import com.holub.life.api.BoardState;
import com.holub.life.api.GridPos;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Optional;

/** Draws one committed board; each cell's color comes from {@link CellPainters}. */
public final class BoardPanel extends JPanel {
    private static final Color GRID = new Color(0xC8C8C8);
    private BoardState board;

    public BoardPanel() {
        setPreferredSize(new Dimension(512, 512));
        setBackground(CellPainters.DEFAULT_DEAD);
    }

    public void setBoard(BoardState board) {
        this.board = board;
        repaint();
    }

    /** Board position under a pixel of this panel, if any. */
    public Optional<GridPos> cellAt(int pixelX, int pixelY) {
        if (board == null || pixelX < 0 || pixelY < 0 || pixelX >= getWidth() || pixelY >= getHeight())
            return Optional.empty();
        return Optional.of(new GridPos(pixelX * board.size().width() / getWidth(),
                pixelY * board.size().height() / getHeight()));
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (board == null) return;
        int columns = board.size().width(), rows = board.size().height();
        for (int y = 0; y < rows; y++) {
            int top = y * getHeight() / rows, bottom = (y + 1) * getHeight() / rows;
            for (int x = 0; x < columns; x++) {
                int left = x * getWidth() / columns, right = (x + 1) * getWidth() / columns;
                g.setColor(CellPainters.colorOf(board.cellAt(new GridPos(x, y))));
                g.fillRect(left, top, right - left, bottom - top);
            }
        }
        if (getWidth() / columns >= 4 && getHeight() / rows >= 4) {
            g.setColor(GRID);
            for (int x = 1; x < columns; x++) g.drawLine(x * getWidth() / columns, 0, x * getWidth() / columns, getHeight());
            for (int y = 1; y < rows; y++) g.drawLine(0, y * getHeight() / rows, getWidth(), y * getHeight() / rows);
        }
    }
}
