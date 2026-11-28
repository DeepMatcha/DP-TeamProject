package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.GridPos;
import com.holub.life.api.Pattern;
import java.awt.Point;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyLifeCodecTest {
    private static final CellType ALIVE = type("core:alive", true);
    private static final CellType EMPTY = type("core:empty", false);
    private static final CellTypeLookup TYPES = new CellTypeLookup() {
        @Override
        public Optional<CellType> resolve(String typeId) {
            return switch (typeId) {
                case "core:alive" -> Optional.of(ALIVE);
                case "core:empty" -> Optional.of(EMPTY);
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<String> idOf(CellType type) {
            if (type == ALIVE || type == EMPTY) {
                return Optional.of(type.id());
            }
            return Optional.empty();
        }
    };

    private final LegacyLifeCodec codec = new LegacyLifeCodec();

    @Test
    void readsOriginalEncodingWithoutMovingPatternToTheOrigin() throws IOException {
        byte[] file = legacyFile(new LinkedList<>(List.of(new Point(2, 5), new Point(9, 7))));
        var input = new TrackingInput(file);

        Pattern pattern = codec.load(input, TYPES);

        assertEquals(new BoardSize(10, 8), pattern.size());
        assertEquals(List.of(liveAt(2, 5), liveAt(9, 7)), pattern.cells());
        assertSame(ALIVE, pattern.cells().getFirst().value().type());
        assertFalse(input.closed);
    }

    @Test
    void writesListOfPointsCompatibleWithOriginalReaderAndKeepsStreamOpen() throws Exception {
        var output = new TrackingOutput();
        Pattern pattern = new Pattern(new BoardSize(64, 64), List.of(liveAt(3, 7), liveAt(0, 63)));

        codec.save(pattern, output, TYPES);

        assertFalse(output.closed);
        try (var originalReader = new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            Object decoded = originalReader.readObject();
            assertInstanceOf(LinkedList.class, decoded);
            assertEquals(List.of(new Point(3, 7), new Point(0, 63)), decoded);
        }
    }

    @Test
    void readsAnEmptyLegacyBoard() throws IOException {
        Pattern pattern = read(legacyFile(new LinkedList<Point>()));

        assertEquals(new BoardSize(1, 1), pattern.size());
        assertTrue(pattern.cells().isEmpty());
    }

    @Test
    void validatesUnrepresentablePatternsBeforeWritingAnything() {
        var output = new TrackingOutput();
        var sameIdDifferentObject = type("core:alive", true);
        var board = new BoardSize(2, 2);
        var origin = new GridPos(0, 0);

        var badEdits = List.of(
                new CellEdit(origin, new CellState(sameIdDifferentObject, Map.of())),
                new CellEdit(origin, new CellState(EMPTY, Map.of())),
                new CellEdit(origin, new CellState(ALIVE, Map.of("age", "3"))),
                liveAt(2, 0));
        for (CellEdit edit : badEdits) {
            var pattern = new Pattern(board, List.of(liveAt(0, 0), edit));
            assertThrows(IOException.class, () -> codec.save(pattern, output, TYPES));
            assertEquals(0, output.size());
            assertFalse(output.closed);
        }
    }

    @Test
    void rejectsUnexpectedTypesAndCoordinates() throws IOException {
        var invalidFiles = List.of(
                new Point(1, 1),
                new LinkedList<>(List.of(new Date(0))),
                new LinkedList<>(List.of(new LinkedList<>(List.of(new Point(1, 1))))),
                new LinkedList<>(List.of("not a point")),
                new LinkedList<>(List.of(new Point(-1, 2))),
                new LinkedList<>(List.of(new Point(Integer.MAX_VALUE, 0))));
        for (Object invalid : invalidFiles) {
            byte[] file = legacyFile(invalid);
            assertThrows(IOException.class, () -> read(file));
        }
    }

    @Test
    void rejectsATruncatedLegacyFileWithoutClosingInput() throws IOException {
        byte[] file = legacyFile(new LinkedList<>(List.of(new Point(2, 5))));
        var input = new TrackingInput(Arrays.copyOf(file, file.length - 2));

        assertThrows(IOException.class, () -> codec.load(input, TYPES));
        assertFalse(input.closed);
    }

    private Pattern read(byte[] file) throws IOException {
        return codec.load(new ByteArrayInputStream(file), TYPES);
    }

    // The original NeighborhoodState.flush used this ObjectOutputStream encoding.
    private static byte[] legacyFile(Object legacyValue) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var encoder = new ObjectOutputStream(out)) {
            encoder.writeObject(legacyValue);
        }
        return out.toByteArray();
    }

    private static CellEdit liveAt(int x, int y) {
        return new CellEdit(new GridPos(x, y), new CellState(ALIVE, Map.of()));
    }

    private static CellType type(String id, boolean alive) {
        return new CellType(id) {
            @Override
            public boolean isAlive(CellState state) {
                return alive;
            }
        };
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        private boolean closed;

        TrackingInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class TrackingOutput extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
