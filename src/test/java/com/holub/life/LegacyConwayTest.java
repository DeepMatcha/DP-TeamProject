package com.holub.life;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyConwayTest {
    @ParameterizedTest(name = "alive={0}, live neighbors={1}, next alive={2}")
    @CsvSource({
        "false, 2, false",
        "false, 3, true",
        "false, 4, false",
        "true, 1, false",
        "true, 2, true",
        "true, 3, true",
        "true, 4, false"
    })
    void conwayRuleAppliesOnTransition(boolean alive, int liveNeighbors, boolean nextAlive) {
        var center = new Resident();
        if (alive) {
            click(center);
        }

        var neighbor = new Resident();
        click(neighbor);
        Cell[] neighbors = new Cell[8];
        Arrays.fill(neighbors, Cell.DUMMY);
        Arrays.fill(neighbors, 0, liveNeighbors, neighbor);

        center.figureNextState(neighbors[0], neighbors[1], neighbors[2], neighbors[3],
                neighbors[4], neighbors[5], neighbors[6], neighbors[7]);

        assertEquals(alive, center.isAlive(), "calculation keeps the current cell until transition");
        center.transition();
        assertEquals(nextAlive, center.isAlive());
    }

    private static void click(Resident cell) {
        cell.userClicked(new Point(0, 0), new Rectangle(0, 0, 1, 1));
    }
}
