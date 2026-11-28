package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.GridPos;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Board layout and hit-testing as arithmetic: no window, no painting. */
class BoardGeometryTest {
    @Test
    void wideBoardIsCenteredWithItsAspectRatio() {
        var geometry = new BoardGeometry(new BoardSize(4, 2), 512, 512);

        assertEquals(new Rectangle(0, 128, 512, 256), geometry.gridBounds());
        assertEquals(Optional.of(new GridPos(3, 1)), geometry.positionAt(new Point(500, 300)));
        assertEquals(Optional.empty(), geometry.positionAt(new Point(10, 10)), "the margin is not a cell");
    }

    @Test
    void everyPaintedCellIsHitByItsOwnFirstAndLastPixel() {
        // Sizes that do not divide evenly, so cell edges are rounded.
        var size = new BoardSize(7, 5);
        var geometry = new BoardGeometry(size, 500, 333);

        for (int y = 0; y < size.height(); y++) {
            for (int x = 0; x < size.width(); x++) {
                var cell = Optional.of(new GridPos(x, y));
                var firstPixel = new Point(geometry.columnEdge(x), geometry.rowEdge(y));
                var lastPixel = new Point(geometry.columnEdge(x + 1) - 1, geometry.rowEdge(y + 1) - 1);
                assertEquals(cell, geometry.positionAt(firstPixel));
                assertEquals(cell, geometry.positionAt(lastPixel));
            }
        }
    }
}
