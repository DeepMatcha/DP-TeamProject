package com.holub.life.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GridPos;
import com.holub.life.api.NeighborBoard;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SnapshotNeighborBoardTest {
    private static final CellType EMPTY = new CellType("test:empty") {
        @Override
        public boolean isAlive(CellState state) {
            return false;
        }
    };

    @Test
    void boundedAccessorIsAttachedToOneSnapshotAndRejectsOutsideCoordinates() {
        BoardState board = board(3, 2);
        Accessor<GridPos> accessor = new BoundedAccessor().bind(board);
        GridPos pos = new GridPos(2, 1);
        assertSame(board.cellAt(pos), accessor.access(pos).orElseThrow().state());
        assertTrue(accessor.access(new GridPos(3, 1)).isEmpty());
        assertTrue(accessor.access(new GridPos(2, -1)).isEmpty());
    }

    @Test
    void boundedWindowClipsAtEdgesAndUsesChebyshevDistance() {
        BoardState board = board(3, 2);
        GridPos center = new GridPos(0, 0);
        NeighborBoard window = new SnapshotNeighborBoard(
            board, new BoundedAccessor().bind(board), center, 1, true);
        assertEquals(window.center(), window.atOffset(0, 0).orElseThrow());
        assertEquals(1, window.distance());
        assertEquals(new GridPos(1, 1), window.atOffset(1, 1).orElseThrow().canonicalPos());
        assertTrue(window.atOffset(2, 0).isEmpty());
        assertTrue(window.atOffset(-1, 0).isEmpty());
        assertEquals(List.of(new GridPos(1, 0), new GridPos(0, 1), new GridPos(1, 1)), positions(window));
        assertThrows(UnsupportedOperationException.class, () -> window.cells().clear());
    }

    @Test
    void mappedNeighborsAreUniqueButSeparateOffsetsCanReferToTheSameCell() {
        BoardState board = board(2, 2);
        NeighborBoard window = new SnapshotNeighborBoard(
            board, wrappingAccessor(board), new GridPos(0, 0), 1, false);
        assertEquals(window.atOffset(1, 0), window.atOffset(-1, 0));
        assertEquals(new GridPos(1, 0), window.atOffset(-1, 0).orElseThrow().canonicalPos());
        assertEquals(List.of(new GridPos(1, 0), new GridPos(0, 1), new GridPos(1, 1)), positions(window));

        BoardState oneCell = board(1, 1);
        NeighborBoard single = new SnapshotNeighborBoard(
            oneCell, wrappingAccessor(oneCell), new GridPos(0, 0), 1, false);
        assertEquals(single.center(), single.atOffset(-1, 0).orElseThrow());
        assertTrue(single.cells().isEmpty());
    }

    @Test
    void wholeBoardDistanceReturnsCanonicalCellsEvenWhenAnAccessorMasksThem() {
        BoardState board = board(3, 2);
        NeighborBoard window = new SnapshotNeighborBoard(
            board, ignored -> Optional.empty(), new GridPos(1, 0), Integer.MAX_VALUE, false);
        assertEquals(List.of(new GridPos(0, 0), new GridPos(2, 0), new GridPos(0, 1),
            new GridPos(1, 1), new GridPos(2, 1)), positions(window));
    }

    private static List<GridPos> positions(NeighborBoard window) {
        return window.cells().stream().map(CellSample::canonicalPos).toList();
    }

    private static Accessor<GridPos> wrappingAccessor(BoardState board) {
        return position -> {
            var canonical = new GridPos(
                Math.floorMod(position.x(), board.size().width()),
                Math.floorMod(position.y(), board.size().height()));
            return Optional.of(new CellSample(canonical, board.cellAt(canonical)));
        };
    }

    private static BoardState board(int width, int height) {
        return new BoardState(new BoardSize(width, height), 0, 12,
            Collections.nCopies(width * height, new CellState(EMPTY, Map.of())));
    }
}
