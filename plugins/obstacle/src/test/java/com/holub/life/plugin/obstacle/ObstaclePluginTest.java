package com.holub.life.plugin.obstacle;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GridPos;
import com.holub.life.api.ToolInput;
import com.holub.life.plugin.view2d.CellPainters;
import com.holub.life.plugin.view2d.View2dPlugin;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObstaclePluginTest {
    private static final CellType MOSS = new CellType("test:moss") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    private static final Map<Character, CellState> LEGEND =
            Map.of('w', ObstacleType.STATE, 'm', new CellState(MOSS, Map.of()));

    private final FakeHost host = new FakeHost().load(new View2dPlugin()).load(new ObstaclePlugin());

    private static BoardState board(String... rows) { return Boards.parse(LEGEND, rows); }

    @Test
    void withoutWallsItIsConway_blockIsStable() {
        BoardState block = board("....", ".##.", ".##.", "....");

        assertEquals(block.cells(), host.step(block).cells());
    }

    @Test
    void withoutWallsItIsConway_blinkerOscillates() {
        BoardState vertical = board(".#.", ".#.", ".#.");
        BoardState horizontal = board("...", "###", "...");

        assertEquals(horizontal.cells(), host.step(vertical).cells());
        assertEquals(vertical.cells(), host.step(host.step(vertical)).cells());
    }

    @Test
    void withoutWallsItIsConway_lonelyAndCrowdedCellsDie() {
        assertEquals(board("...", "...", "...").cells(), host.step(board("...", ".#.", "...")).cells(), "lonely");
        assertSame(FakeHost.EMPTY, Boards.at(host.step(board("###", "###", "###")), 1, 1).type(), "crowded");
    }

    @Test
    void wallStaysEvenWhereConwayWouldGiveBirth() {
        // The blinker's horizontal phase would be born at (2,1), but a wall stands there.
        BoardState next = host.step(board(".#.", ".#w", ".#."));

        assertEquals(board("...", "##w", "...").cells(), next.cells());
    }

    @Test
    void wallCountsAsADeadNeighbor() {
        // Two living neighbors plus a wall are not three: no birth at (1,1).
        BoardState next = host.step(board("#.#", "...", ".w."));

        assertSame(FakeHost.EMPTY, Boards.at(next, 1, 1).type());
        assertSame(ObstacleType.INSTANCE, Boards.at(next, 1, 2).type());
    }

    @Test
    void wallsSurviveManyTicksNextToActivity() {
        BoardState current = board("w.#.w", "w.#.w", "w.#.w");
        for (int tick = 0; tick < 10; tick++) current = host.step(current);

        for (int y = 0; y < 3; y++) {
            assertSame(ObstacleType.INSTANCE, Boards.at(current, 0, y).type());
            assertSame(ObstacleType.INSTANCE, Boards.at(current, 4, y).type());
        }
        assertEquals(10, current.generation());
    }

    @Test
    void unknownTypeThatIsNotAliveIsLeftUnchanged() {
        // Three living neighbors around the moss: the rule gives birth only on the host's empty cell.
        BoardState next = host.step(board("###", ".m.", "..."));

        assertSame(MOSS, Boards.at(next, 1, 1).type());
    }

    @Test
    void toolBuildsAWallAndRemovesItAgain() {
        WallTool tool = (WallTool) host.inputTool(WallTool.ID).orElseThrow();
        BoardState empty = board("..", "..");
        var accessor = host.accessor(FakeHost.BOUNDED_ACCESSOR).orElseThrow();
        ToolInput click = new ToolInput(new GridPos(1, 0), "click", Map.of());

        BoardPatch build = tool.apply(click, empty, accessor.bind(empty));
        assertEquals(List.of(new CellEdit(new GridPos(1, 0), ObstacleType.STATE)), build.edits());

        BoardState walled = Boards.with(empty, 1, 0, ObstacleType.STATE);
        BoardPatch remove = tool.apply(click, walled, accessor.bind(walled));
        assertEquals(List.of(new CellEdit(new GridPos(1, 0), FakeHost.EMPTY_STATE)), remove.edits());
    }

    @Test
    void toolChangesNothingOutsideTheBoard() {
        WallTool tool = (WallTool) host.inputTool(WallTool.ID).orElseThrow();
        BoardState empty = board("..");
        var accessor = host.accessor(FakeHost.BOUNDED_ACCESSOR).orElseThrow().bind(empty);

        assertTrue(tool.apply(new ToolInput(new GridPos(5, 5), "click", Map.of()), empty, accessor).edits().isEmpty());
    }

    @Test
    void wallIsRegisteredByIdAndVisibleInTheTwoDimensionalView() {
        assertSame(ObstacleType.INSTANCE, host.resolve("obstacle:wall").orElseThrow());
        assertEquals(ObstaclePainters.WALL, CellPainters.colorOf(ObstacleType.STATE));
    }

    @Test
    void disablesItselfWhenTheViewLibraryIsMissing() {
        FakeHost bare = new FakeHost().load(new ObstaclePlugin("no.such.ViewLibrary"));

        assertTrue(bare.registrations().isEmpty());
        assertTrue(bare.resolve("obstacle:wall").isEmpty());
    }
}
