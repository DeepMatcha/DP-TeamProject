package com.holub.life.host;

import com.holub.life.api.BoardState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * The original Load and Store commands for {@code .life} files as host operations.
 * File I/O runs off the operation thread; the board changes only through the controller.
 * Dialogs live in {@link PatternMenus}, so these commands are testable without a display.
 */
final class PatternFileCommands {
    private final HostController host;

    PatternFileCommands(HostController host) {
        this.host = host;
    }

    /** Reads and validates the pattern first; the board is replaced (and playback stopped) only on success. */
    CompletionStage<BoardState> load(Path source) {
        var loading = CompletableFuture.supplyAsync(() -> {
            try {
                return LegacyPatternFiles.load(source, host.registry());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
        return loading.thenCompose(pattern -> host.replacePattern(pattern, CoreGame.EMPTY));
    }

    /** Stops playback, then stores the board as it is at that operation boundary. */
    CompletionStage<Void> store(Path destination) {
        return host.setRunning(false).thenCompose(ignored -> host.session()).thenAcceptAsync(snapshot -> {
            try {
                LegacyPatternFiles.store(destination, snapshot.board(), host.registry());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
    }

    /** The chosen path, with the {@code .life} extension added when it is missing. */
    static Path destination(Path chosen) {
        String name = chosen.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".life") ? chosen : chosen.resolveSibling(name + ".life");
    }
}
