package com.holub.life.host;

import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GridPos;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertSame;

class CoreConwayRuleTest {
    private static final CellState LIVE = new CellState(CoreCells.ALIVE, Map.of());
    private static final CellState DEAD = new CellState(CoreCells.EMPTY, Map.of());

    @Test
    void appliesBirthSurvivalAndDeathAtTheConwayThresholds() {
        assertSame(CoreCells.ALIVE, next(DEAD, 3).type());
        assertSame(CoreCells.EMPTY, next(DEAD, 2).type());
        assertSame(CoreCells.EMPTY, next(DEAD, 4).type());

        assertSame(CoreCells.ALIVE, next(LIVE, 2).type());
        assertSame(CoreCells.ALIVE, next(LIVE, 3).type());
        assertSame(CoreCells.EMPTY, next(LIVE, 1).type());
        assertSame(CoreCells.EMPTY, next(LIVE, 4).type());
    }

    @Test
    void leavesExtensionStateAndSurvivingCorePropertiesIntact() {
        CellType extension = new CellType("demo:extension") {
            @Override
            public boolean isAlive(CellState state) {
                return false;
            }
        };
        var obstacle = new CellState(extension, Map.of("demo:property", "value"));
        var annotatedLive = new CellState(CoreCells.ALIVE, Map.of("demo:age", "2"));

        assertSame(obstacle, next(obstacle, 3));
        assertSame(annotatedLive, next(annotatedLive, 2));
    }

    private static CellState next(CellState center, int livingNeighbors) {
        var cells = new ArrayList<CellSample>();
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                if (x != 0 || y != 0) {
                    cells.add(new CellSample(new GridPos(x + 1, y + 1),
                            cells.size() < livingNeighbors ? LIVE : DEAD));
                }
            }
        }
        var neighborhood = new TestNeighborhood(
                new CellSample(new GridPos(1, 1), center), List.copyOf(cells));
        return new CoreConwayRule().figureNextState(neighborhood, new StepContext(0, new Random(42)));
    }

    private record TestNeighborhood(CellSample center, List<CellSample> cells) implements NeighborBoard {
        @Override
        public int distance() {
            return 1;
        }

        @Override
        public Optional<CellSample> atOffset(int dx, int dy) {
            if (dx == 0 && dy == 0) {
                return Optional.of(center);
            }
            return cells.stream().filter(sample -> sample.canonicalPos().equals(
                    new GridPos(center.canonicalPos().x() + dx, center.canonicalPos().y() + dy)))
                    .findFirst();
        }
    }
}
