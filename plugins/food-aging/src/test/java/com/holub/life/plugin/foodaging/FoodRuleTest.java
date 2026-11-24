package com.holub.life.plugin.foodaging;

import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodRuleTest {
    private static final CellType WALL = new CellType("test:wall") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    private static final CellState WALL_STATE = new CellState(WALL, Map.of());
    private static final Map<Character, CellState> LEGEND =
            Map.of('f', FoodType.STATE, 's', BurstSeedType.STATE, 'w', WALL_STATE);

    private final FakeHost host = new FakeHost();

    private BoardState tick(FoodRule rule, BoardState board) {
        return FakeHost.runStage(rule, board, host.accessor(FakeHost.BOUNDED_ACCESSOR).orElseThrow(), 0);
    }

    private FoodRule rule(double seedChance, double burstChance, int radius) {
        return new FoodRule(host, seedChance, burstChance, radius);
    }

    @Test
    void seedAppearsFirstAndRemovesItselfOnTheFollowingTick() {
        FoodRule alwaysSeed = rule(1.0, 1.0, 1);

        BoardState first = tick(alwaysSeed, Boards.parse("...", "...", "..."));
        assertTrue(first.cells().stream().allMatch(BurstSeedType.STATE::equals), "tick 1: seeds, no food yet");

        BoardState second = tick(alwaysSeed, first);
        assertTrue(second.cells().stream().allMatch(cell -> cell.type() == FakeHost.EMPTY), "tick 2: every seed is gone");
    }

    @Test
    void burstTurnsEmptyCellsAroundTheSeedIntoFoodOneTickLater() {
        BoardState board = Boards.parse(LEGEND,
                ".......",
                ".......",
                ".......",
                "...s...",
                ".......",
                ".......",
                ".......");

        BoardState next = tick(rule(0.0, 1.0, 2), board);

        assertSame(FakeHost.EMPTY, Boards.at(next, 3, 3).type(), "the seed itself is gone");
        for (int y = 0; y < 7; y++)
            for (int x = 0; x < 7; x++) {
                int distance = Math.max(Math.abs(x - 3), Math.abs(y - 3));
                CellType type = Boards.at(next, x, y).type();
                if (distance == 1) assertSame(FoodType.INSTANCE, type, "chance 1.0 at distance 1");
                if (distance == 3) assertSame(FakeHost.EMPTY, type, "outside the burst radius");
            }
        long outerRing = next.cells().stream().filter(FoodType.STATE::equals).count() - 8;
        assertTrue(outerRing > 0 && outerRing < 16, "chance 0.5 at distance 2 gives some, not all: " + outerRing);
    }

    @Test
    void foodNextToALivingCellIsEaten() {
        BoardState next = tick(rule(0.0, 0.0, 1), Boards.parse(LEGEND, "#f.f"));

        assertSame(FakeHost.EMPTY, Boards.at(next, 1, 0).type(), "eaten");
        assertSame(FoodType.INSTANCE, Boards.at(next, 3, 0).type(), "no living neighbor, stays");
    }

    @Test
    void nothingAppearsOnCellsThatAreNotEmpty() {
        BoardState board = Boards.parse(LEGEND, "#w", "w#");

        assertEquals(board, tick(rule(1.0, 1.0, 1), board), "alive cells and unknown types are left alone");
    }

    @Test
    void sameSourceGivesSameResult() {
        BoardState source = Boards.withSeed(Boards.filled(16, 16, FakeHost.EMPTY_STATE), 7);
        FoodRule half = rule(0.5, 1.0, 2);

        assertEquals(tick(half, source), tick(half, source));
    }

    @Test
    void differentBoardSeedGivesDifferentFood() {
        FoodRule half = rule(0.5, 1.0, 2);
        BoardState empty = Boards.filled(16, 16, FakeHost.EMPTY_STATE);

        assertNotEquals(tick(half, Boards.withSeed(empty, 1)).cells(), tick(half, Boards.withSeed(empty, 2)).cells());
    }

    @Test
    void inputBoardIsNotModified() {
        BoardState board = Boards.parse(LEGEND, "s..", "#f.");
        BoardState copy = new BoardState(board.size(), board.generation(), board.seed(), board.cells());

        tick(rule(1.0, 1.0, 2), board);

        assertEquals(copy, board);
    }
}
