package com.holub.life.host;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes a file through a temporary sibling, so a failed save never damages the existing file. */
final class AtomicFileWriter {
    @FunctionalInterface
    interface Encoder {
        void write(OutputStream stream) throws IOException;
    }

    static void store(Path path, Encoder encoder) throws IOException {
        Path destination = path.toAbsolutePath();
        Path temporary = Files.createTempFile(destination.getParent(), ".life-", ".tmp");
        try {
            try (var stream = Files.newOutputStream(temporary)) {
                encoder.write(stream);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private AtomicFileWriter() {}
}
