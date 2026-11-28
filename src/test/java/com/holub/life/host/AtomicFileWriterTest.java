package com.holub.life.host;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicFileWriterTest {
    @Test
    void failedEncoderPreservesExistingSaveAndRemovesPartialOutput(@TempDir Path directory) throws IOException {
        Path save = directory.resolve("checkpoint");
        Files.writeString(save, "previous game");

        assertThrows(IOException.class, () -> AtomicFileWriter.store(save, stream -> {
            stream.write(42);
            throw new IOException("encoding stopped");
        }));

        assertEquals("previous game", Files.readString(save));
        try (var contents = Files.list(directory)) {
            assertEquals(1L, contents.count());
        }
    }
}
