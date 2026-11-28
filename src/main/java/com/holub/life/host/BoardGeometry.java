package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.GridPos;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Optional;

/**
 * Aspect-preserving layout of a board inside a panel. Painting and hit-testing share it,
 * so a click always lands on the cell drawn under the pointer. Pure arithmetic, no Swing.
 */
record BoardGeometry(BoardSize board, int panelWidth, int panelHeight) {

    /** The largest centered rectangle with the board's aspect ratio. */
    Rectangle gridBounds() {
        double scale = Math.min((double) panelWidth / board.width(), (double) panelHeight / board.height());
        int width = (int) (board.width() * scale);
        int height = (int) (board.height() * scale);
        return new Rectangle((panelWidth - width) / 2, (panelHeight - height) / 2, width, height);
    }

    /** Pixel x of the left edge of a column; {@code board.width()} gives the right edge of the grid. */
    int columnEdge(int column) {
        Rectangle area = gridBounds();
        return area.x + scaled(column, area.width, board.width());
    }

    /** Pixel y of the top edge of a row; {@code board.height()} gives the bottom edge of the grid. */
    int rowEdge(int row) {
        Rectangle area = gridBounds();
        return area.y + scaled(row, area.height, board.height());
    }

    Optional<GridPos> positionAt(Point point) {
        Rectangle area = gridBounds();
        if (!area.contains(point)) {
            return Optional.empty();
        }
        // Invert the same rounded-down boundaries used for painting.
        int x = (int) (((point.x - area.x + 1L) * board.width() - 1) / area.width);
        int y = (int) (((point.y - area.y + 1L) * board.height() - 1) / area.height);
        return Optional.of(new GridPos(x, y));
    }

    private static int scaled(int coordinate, int pixels, int total) {
        return (int) ((long) coordinate * pixels / total);
    }
}
