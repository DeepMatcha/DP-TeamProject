package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.GridPos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Load and Store commands of the Grid menu, run without a window or file dialog. */
class PatternFileCommandsTest {
    // A 2x2 block is stable, so a tick during playback does not change it.
    private static final List<GridPos> BLOCK = List.of(
            new GridPos(0, 0), new GridPos(1, 0), new GridPos(0, 1), new GridPos(1, 1));

    @TempDir
    Path files;

    @Test
    void storeThenLoadRoundTripsThePatternAndBothStopPlayback() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(8, 8), 1L)) {
            var commands = new PatternFileCommands(host);
            Path saved = files.resolve("block.life");
            await(host.apply(new BoardPatch(BLOCK.stream().map(cell -> new CellEdit(cell, CoreGame.ALIVE)).toList())));

            await(host.playAtInterval(60_000));
            await(commands.store(saved));
            assertTrue(Files.exists(saved));
            assertFalse(await(host.playbackState()).running(), "Store stops playback");

            await(host.clear(CoreGame.EMPTY));
            await(host.playAtInterval(60_000));
            var loaded = await(commands.load(saved));
            assertEquals(BLOCK.size(), loaded.cells().stream().filter(cell -> cell.type() == CoreCells.ALIVE).count());
            for (GridPos cell : BLOCK) {
                assertSame(CoreCells.ALIVE, loaded.cellAt(cell).type(), cell.toString());
            }
            assertFalse(await(host.playbackState()).running(), "Load stops playback");
        }
    }

    @Test
    void aFailedLoadLeavesTheBoardAndPlaybackAsTheyWere() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(8, 8), 1L)) {
            var commands = new PatternFileCommands(host);
            await(host.apply(new BoardPatch(BLOCK.stream().map(cell -> new CellEdit(cell, CoreGame.ALIVE)).toList())));
            await(host.playAtInterval(60_000));
            var before = host.model().snapshot();

            assertThrows(ExecutionException.class, () -> await(commands.load(files.resolve("missing.life"))));

            assertSame(before, host.model().snapshot());
            assertTrue(await(host.playbackState()).running());
        }
    }

    @Test
    void storeAddsTheLifeExtensionOnlyWhenItIsMissing() {
        assertEquals(files.resolve("glider.life"), PatternFileCommands.destination(files.resolve("glider")));
        assertEquals(files.resolve("glider.LIFE"), PatternFileCommands.destination(files.resolve("glider.LIFE")));
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
