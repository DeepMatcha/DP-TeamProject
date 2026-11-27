package com.holub.life.plugin.foodaging;

import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgingRuleTest {
    private static final CellType WALL = new CellType("test:wall") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    private static final CellState WALL_STATE = new CellState(WALL, Map.of());

    private final FakeHost host = new FakeHost();
    private final AgingRule rule = new AgingRule(host, 3);

    private BoardState tick(BoardState board) {
        return FakeHost.runStage(rule, board, host.accessor(FakeHost.BOUNDED_ACCESSOR).orElseThrow(), 0);
    }

    private static CellState aliveAged(int age) {
        return new CellState(FakeHost.ALIVE, Map.of(AgingRule.AGE, Integer.toString(age)));
    }

    @Test
    void livingCellGrowsOneTickOlderEachTick() {
        BoardState board = Boards.parse("#");

        board = tick(board);
        assertEquals(1, AgingRule.ageOf(Boards.at(board, 0, 0)));
        board = tick(board);
        assertEquals(2, AgingRule.ageOf(Boards.at(board, 0, 0)));
        assertSame(FakeHost.ALIVE, Boards.at(board, 0, 0).type(), "still the host's alive type");
    }

    @Test
    void cellDiesOfOldAgeAndBecomesTheHostEmptyCell() {
        BoardState board = Boards.parse(Map.of('o', aliveAged(3)), "o");

        CellState next = Boards.at(tick(board), 0, 0);

        assertSame(FakeHost.EMPTY, next.type());
        assertTrue(next.properties().isEmpty(), "no age is left behind on the empty cell");
    }

    @Test
    void cellAtMaxAgeMinusOneSurvivesOneMoreTick() {
        BoardState board = Boards.parse(Map.of('o', aliveAged(2)), "o");

        assertEquals(aliveAged(3), Boards.at(tick(board), 0, 0));
    }

    @Test
    void foodNextToCellResetsItsAgeInsteadOfLettingItDie() {
        BoardState board = Boards.parse(Map.of('o', aliveAged(3), 'f', FoodType.STATE), "of");

        assertEquals(aliveAged(0), Boards.at(tick(board), 0, 0));
    }

    @Test
    void foodTwoCellsAwayDoesNotFeed() {
        BoardState board = Boards.parse(Map.of('o', aliveAged(1), 'f', FoodType.STATE), "o.f");

        assertEquals(aliveAged(2), Boards.at(tick(board), 0, 0));
    }

    @Test
    void ageBelongsToEachCoordinateNotToTheSharedType() {
        BoardState board = Boards.parse(Map.of('y', aliveAged(0), 'o', aliveAged(2)), "y..o");

        BoardState next = tick(board);

        assertEquals(1, AgingRule.ageOf(Boards.at(next, 0, 0)));
        assertEquals(3, AgingRule.ageOf(Boards.at(next, 3, 0)));
        assertTrue(FakeHost.ALIVE_STATE.properties().isEmpty(), "nothing was stored on the singleton's shared state");
    }

    @Test
    void cellsThatAreNotAliveAreReturnedUnchanged() {
        BoardState board = Boards.parse(Map.of('f', FoodType.STATE, 'w', WALL_STATE, 's', BurstSeedType.STATE), ".fws");

        assertEquals(board, tick(board));
    }

    @Test
    void inputBoardIsNotModified() {
        BoardState board = Boards.parse("##", "..");
        BoardState copy = new BoardState(board.size(), board.generation(), board.seed(), board.cells());

        tick(board);

        assertEquals(copy, board);
    }
}
