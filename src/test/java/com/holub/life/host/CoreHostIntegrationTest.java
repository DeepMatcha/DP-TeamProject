package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;
import com.holub.life.api.ToolInput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CoreHostIntegrationTest {
    @TempDir
    Path files;

    @Test
    void defaultHostEditsTicksClearsAndRoundTripsALegacyPattern() throws Exception {
        var size = new BoardSize(64, 64);
        var horizontal = Set.of(new GridPos(30, 31), new GridPos(31, 31), new GridPos(32, 31));
        var vertical = Set.of(new GridPos(31, 30), new GridPos(31, 31), new GridPos(31, 32));
        try (var host = CoreGame.newSession(size, 42L)) {
            for (GridPos cell : horizontal) {
                await(host.applyTool(CoreGame.TOGGLE, new ToolInput(cell, "toggle", Map.of())));
            }
            assertEquals(horizontal, alivePositions(host.model().snapshot()));
            var afterTick = await(host.step());
            assertEquals(vertical, alivePositions(afterTick));
            assertEquals(1, afterTick.generation());

            Path patternFile = files.resolve("oscillator.life");
            LegacyPatternFiles.store(patternFile, await(host.session()).board(), host.registry());
            await(host.clear(CoreGame.EMPTY));
            assertTrue(alivePositions(host.model().snapshot()).isEmpty());
            await(host.replacePattern(LegacyPatternFiles.load(patternFile, host.registry()), CoreGame.EMPTY));
            assertEquals(vertical, alivePositions(host.model().snapshot()));
            assertEquals(1, host.model().snapshot().generation());
            assertEquals(horizontal, alivePositions(await(host.step())));
        }
    }

    @Test
    void defaultHostDoesNotWrapTheUpperEdgeBlinker() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(64, 64), 42L)) {
            var horizontal = new BoardPatch(List.of(
                    new CellEdit(new GridPos(30, 0), CoreGame.ALIVE),
                    new CellEdit(new GridPos(31, 0), CoreGame.ALIVE),
                    new CellEdit(new GridPos(32, 0), CoreGame.ALIVE)));
            await(host.apply(horizontal));

            var first = await(host.step());
            assertEquals(Set.of(new GridPos(31, 0), new GridPos(31, 1)), alivePositions(first));
            var second = await(host.step());
            assertTrue(alivePositions(second).isEmpty());
            assertEquals(2L, second.generation());
        }
    }

    @Test
    void unsupportedStateCannotCorruptAnExistingPatternFile() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(2, 2), 42L)) {
            Path file = files.resolve("pattern.life");
            LegacyPatternFiles.store(file, host.model().snapshot(), host.registry());
            byte[] original = Files.readAllBytes(file);
            var decorated = new CellState(CoreCells.ALIVE, Map.of("test:property", "value"));
            var board = await(host.apply(new BoardPatch(List.of(new CellEdit(new GridPos(0, 0), decorated)))));
            assertThrows(IOException.class, () -> LegacyPatternFiles.store(file, board, host.registry()));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static Set<GridPos> alivePositions(BoardState board) {
        var result = new HashSet<GridPos>();
        for (int y = 0; y < board.size().height(); y++) {
            for (int x = 0; x < board.size().width(); x++) {
                var position = new GridPos(x, y);
                var cell = board.cellAt(position);
                if (cell.type().isAlive(cell)) {
                    result.add(position);
                }
            }
        }
        return result;
    }
}
