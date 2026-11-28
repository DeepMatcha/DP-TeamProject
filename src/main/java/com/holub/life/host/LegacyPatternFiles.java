package com.holub.life.host;

import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.GridPos;
import com.holub.life.api.Pattern;
import com.holub.life.api.RegistryView;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Converts a legacy pattern file without accessing the live Model. */
final class LegacyPatternFiles {
    private LegacyPatternFiles() {}

    static Pattern load(Path source, RegistryView registry) throws IOException {
        var codec = registry.patternCodec(LegacyLifeCodec.FORMAT_ID).orElseThrow();
        try (var stream = Files.newInputStream(source)) {
            return codec.load(stream, registry);
        }
    }

    static void store(Path target, BoardState board, RegistryView registry) throws IOException {
        var codec = registry.patternCodec(LegacyLifeCodec.FORMAT_ID).orElseThrow();
        var cells = new ArrayList<CellEdit>();
        for (int y = 0; y < board.size().height(); y++) {
            for (int x = 0; x < board.size().width(); x++) {
                var position = new GridPos(x, y);
                var state = board.cellAt(position);
                if (state.type() != CoreCells.EMPTY || !state.properties().isEmpty()) {
                    cells.add(new CellEdit(position, state));
                }
            }
        }
        var pattern = new Pattern(board.size(), cells);
        AtomicFileWriter.store(target, stream -> codec.save(pattern, stream, registry));
    }
}
