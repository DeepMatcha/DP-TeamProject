package com.holub.life.plugin.toroidal;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.StepContext;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import com.holub.life.testkit.FakeNeighborBoard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ToroidalPluginTest {
    private final FakeHost host = new FakeHost().load(new ToroidalPlugin());

    private Accessor<GridPos> wrap(BoardState board) {
        return host.accessor(ToroidalAccessor.ID).orElseThrow().bind(board);
    }

    private NeighborBoard neighbors(BoardState board, int x, int y, int distance) {
        return new FakeNeighborBoard(board, wrap(board), new GridPos(x, y), distance);
    }

    @Test
    void accessorIsRegisteredUnderItsId() {
        assertEquals(GridPos.class, host.accessor(ToroidalAccessor.ID).orElseThrow().coordinateType());
    }

    @Test
    void positionsInsideTheBoardAreReturnedAsTheyAre() {
        BoardState board = Boards.parse("..", ".#");

        assertEquals(new CellSample(new GridPos(1, 1), FakeHost.ALIVE_STATE), wrap(board).access(new GridPos(1, 1)).orElseThrow());
    }

    @Test
    void negativeAndOverflowingPositionsWrapToTheCanonicalCell() {
        BoardState board = Boards.parse("...", "...", "..#");
        Accessor<GridPos> accessor = wrap(board);

        assertEquals(new GridPos(2, 2), accessor.access(new GridPos(-1, -1)).orElseThrow().canonicalPos());
        assertSame(FakeHost.ALIVE_STATE, accessor.access(new GridPos(-1, -1)).orElseThrow().state());
        assertEquals(new GridPos(0, 1), accessor.access(new GridPos(3, 4)).orElseThrow().canonicalPos());
        assertEquals(new GridPos(2, 0), accessor.access(new GridPos(-7, -6)).orElseThrow().canonicalPos());
    }

    @Test
    void cornerCellHasEightDistinctNeighborsSortedByRowThenColumn() {
        BoardState board = Boards.filled(4, 4, FakeHost.EMPTY_STATE);

        List<GridPos> positions = neighbors(board, 0, 0, 1).cells().stream().map(CellSample::canonicalPos).toList();

        assertEquals(List.of(new GridPos(1, 0), new GridPos(3, 0), new GridPos(0, 1), new GridPos(1, 1),
                new GridPos(3, 1), new GridPos(0, 3), new GridPos(1, 3), new GridPos(3, 3)), positions);
    }

    @Test
    void twoByTwoTorusHasThreeNeighborsAndOneByOneHasNone() {
        assertEquals(3, neighbors(Boards.filled(2, 2, FakeHost.EMPTY_STATE), 0, 0, 1).cells().size());
        assertEquals(0, neighbors(Boards.filled(1, 1, FakeHost.EMPTY_STATE), 0, 0, 1).cells().size());
    }

    @Test
    void centerIsStillOffsetZeroAndOffsetsReachAcrossTheEdge() {
        BoardState board = Boards.parse("#..", "...", "...");
        NeighborBoard corner = neighbors(board, 2, 2, 1);

        assertEquals(corner.center(), corner.atOffset(0, 0).orElseThrow());
        assertSame(FakeHost.ALIVE_STATE, corner.atOffset(1, 1).orElseThrow().state(), "(2,2)+(1,1) wraps to (0,0)");
    }

    @Test
    void ruleCountingNeighborsSeesCellsAcrossTheEdge() {
        // A rule that keeps a cell alive only when it has exactly two living neighbors.
        LifePlugin counting = new LifePlugin() {
            @Override public String id() { return "test:two-neighbors"; }
            @Override public void register(PluginRegistrar registrar) {
                registrar.registerLifecycle(new LifecycleManager(1) {
                    @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                        long living = neighbors.cells().stream().filter(s -> s.state() == FakeHost.ALIVE_STATE).count();
                        return living == 2 ? FakeHost.ALIVE_STATE : FakeHost.EMPTY_STATE;
                    }
                });
            }
        };
        host.load(counting);
        // A row of three across the vertical edge: (3,1), (0,1), (1,1).
        BoardState board = Boards.parse("....", "##.#", "....", "....");

        assertSame(FakeHost.ALIVE_STATE, Boards.at(host.step(board, ToroidalAccessor.ID), 0, 1), "two neighbors when wrapped");
        assertSame(FakeHost.EMPTY_STATE, Boards.at(host.step(board, FakeHost.BOUNDED_ACCESSOR), 0, 1), "one neighbor when bounded");
    }
}
