package com.holub.life.host;

import com.holub.life.Cell;
import com.holub.life.Neighborhood;
import com.holub.life.Resident;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.GridPos;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LegacyPatternInteroperabilityTest {
    private static final BoardSize SIZE = new BoardSize(64, 64);
    private static final long SEED = 582L;
    private static final GridPos INTERIOR = new GridPos(11, 23);
    private static final GridPos RIGHT_EDGE = new GridPos(63, 3);
    private static final Set<GridPos> EXPECTED = Set.of(INTERIOR, RIGHT_EDGE);

    @TempDir
    Path files;

    @Test
    void loadsOriginalStoreWithDuplicatePointsWithoutMovingCellsOrResettingTheSession() throws Exception {
        var original = legacyBoard();
        var surface = new Rectangle(0, 0, SIZE.width(), SIZE.height());
        original.userClicked(point(INTERIOR), surface);
        original.userClicked(point(RIGHT_EDGE), surface);

        Path file = files.resolve("original.life");
        var memento = original.createMemento();
        // Universe.doStore calls transfer again even though createMemento captured the same cells.
        original.transfer(memento, new Point(), Cell.STORE);
        try (var output = Files.newOutputStream(file)) {
            memento.flush(output);
        }
        try (var input = new ObjectInputStream(Files.newInputStream(file))) {
            var points = assertInstanceOf(LinkedList.class, input.readObject());
            assertEquals(4, points.size());
            assertEquals(2, Collections.frequency(points, point(INTERIOR)));
            assertEquals(2, Collections.frequency(points, point(RIGHT_EDGE)));
        }

        try (var host = CoreGame.newSession(SIZE, SEED)) {
            await(host.step());
            await(host.apply(new BoardPatch(List.of(
                    new CellEdit(new GridPos(4, 7), CoreGame.ALIVE)))));

            var pattern = LegacyPatternFiles.load(file, host.registry());
            var board = await(host.replacePattern(pattern, CoreGame.EMPTY));

            assertEquals(EXPECTED, positionsMatching(position -> {
                var cell = board.cellAt(position);
                return cell.type().isAlive(cell);
            }));
            assertEquals(SIZE, board.size());
            assertEquals(1L, board.generation());
            assertEquals(SEED, board.seed());
        }
    }

    @Test
    void originalMementoRestoresTheHostWrittenPatternAtTheSameCoordinates() throws Exception {
        Path file = files.resolve("host.life");
        try (var host = CoreGame.newSession(SIZE, SEED)) {
            var board = await(host.apply(new BoardPatch(EXPECTED.stream()
                    .map(position -> new CellEdit(position, CoreGame.ALIVE))
                    .toList())));
            LegacyPatternFiles.store(file, board, host.registry());
        }

        var original = legacyBoard();
        var loaded = original.createMemento();
        try (var input = Files.newInputStream(file)) {
            loaded.load(input);
        }
        original.transfer(loaded, new Point(), Cell.LOAD);
        var restored = (Cell.Memento) original.createMemento();
        assertEquals(EXPECTED, positionsMatching(position -> restored.isAlive(point(position))));
    }

    private static Neighborhood legacyBoard() {
        return new Neighborhood(8, new Neighborhood(8, new Resident()));
    }

    private static Point point(GridPos position) {
        return new Point(position.x(), position.y());
    }

    private static Set<GridPos> positionsMatching(Predicate<GridPos> matches) {
        var positions = new HashSet<GridPos>();
        for (int y = 0; y < SIZE.height(); y++) {
            for (int x = 0; x < SIZE.width(); x++) {
                var position = new GridPos(x, y);
                if (matches.test(position)) {
                    positions.add(position);
                }
            }
        }
        return positions;
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
