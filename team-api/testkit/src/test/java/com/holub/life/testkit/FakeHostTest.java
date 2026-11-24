package com.holub.life.testkit;

import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.StepContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fake host must itself follow the contract, or plugin tests built on it prove nothing. */
class FakeHostTest {
    private final FakeHost host = new FakeHost();

    @Test
    void coreTypesAreRegisteredBeforeAnyPlugin() {
        assertSame(FakeHost.EMPTY, CoreCellTypes.empty(host));
        assertSame(FakeHost.ALIVE, CoreCellTypes.alive(host));
    }

    @Test
    void neighborWindowFollowsContract() {
        BoardState board = Boards.parse("#..", "...", "..#");
        var accessor = host.accessor(FakeHost.BOUNDED_ACCESSOR).orElseThrow().bind(board);
        NeighborBoard corner = new FakeNeighborBoard(board, accessor, new GridPos(0, 0), 1);

        assertEquals(corner.center(), corner.atOffset(0, 0).orElseThrow());
        assertTrue(corner.atOffset(-1, 0).isEmpty(), "outside a bounded board");
        assertTrue(corner.atOffset(2, 0).isEmpty(), "outside the range");
        assertEquals(List.of(new GridPos(1, 0), new GridPos(0, 1), new GridPos(1, 1)),
                corner.cells().stream().map(sample -> sample.canonicalPos()).toList());

        NeighborBoard whole = new FakeNeighborBoard(board, accessor, new GridPos(1, 1), Integer.MAX_VALUE);
        assertEquals(8, whole.cells().size(), "whole board without the center");
    }

    @Test
    void laterStageReadsWholeResultOfEarlierStageAndGenerationGrowsOnce() {
        // Stage 1 turns every cell alive; stage 2 keeps a cell alive only if all neighbors are alive.
        LifecycleManager fill = rule((neighbors, context) -> FakeHost.ALIVE_STATE);
        LifecycleManager needsFullNeighborhood = rule((neighbors, context) ->
                neighbors.cells().stream().allMatch(s -> s.state() == FakeHost.ALIVE_STATE)
                        ? FakeHost.ALIVE_STATE : FakeHost.EMPTY_STATE);
        host.load(plugin("test:two-stages", fill, needsFullNeighborhood));

        BoardState next = host.step(Boards.parse("#.", ".."));

        assertTrue(next.cells().stream().allMatch(s -> s == FakeHost.ALIVE_STATE));
        assertEquals(1, next.generation());
    }

    @Test
    void sameSourceGivesSameRandomDraws() {
        host.load(plugin("test:random", rule((neighbors, context) ->
                context.random().nextBoolean() ? FakeHost.ALIVE_STATE : FakeHost.EMPTY_STATE)));
        BoardState source = Boards.withSeed(Boards.filled(8, 8, FakeHost.EMPTY_STATE), 42);

        assertEquals(host.step(source), host.step(source));
    }

    private interface Rule { CellState next(NeighborBoard neighbors, StepContext context); }

    private static LifecycleManager rule(Rule rule) {
        return new LifecycleManager(1) {
            @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                return rule.next(neighbors, context);
            }
        };
    }

    private static LifePlugin plugin(String id, LifecycleManager... rules) {
        return new LifePlugin() {
            @Override public String id() { return id; }
            @Override public void register(com.holub.life.api.PluginRegistrar registrar) {
                for (LifecycleManager rule : rules) registrar.registerLifecycle(rule);
            }
        };
    }
}
