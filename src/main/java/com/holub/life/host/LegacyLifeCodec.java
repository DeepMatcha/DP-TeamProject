package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.GridPos;
import com.holub.life.api.Pattern;
import com.holub.life.api.PatternCodec;
import java.awt.Point;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes Holub's serialized list of live coordinates. The format
 * contains no board dimensions, so load reports the smallest extent anchored
 * at (0, 0). The caller owns both streams and decides how to place the pattern.
 */
public final class LegacyLifeCodec implements PatternCodec {
    public static final String FORMAT_ID = "core:legacy-life";

    private static final String ALIVE_ID = "core:alive";
    private static final int MAX_POINTS = 65_536;
    private static final int MAX_SIDE = 16_384;
    private static final long MAX_BYTES = 2L * 1024 * 1024;

    @Override
    public String formatId() {
        return FORMAT_ID;
    }

    @Override
    public void save(Pattern pattern, OutputStream out, CellTypeLookup types) throws IOException {
        if (pattern.cells().size() > MAX_POINTS) {
            throw new IOException("Too many live cells for a legacy .life file");
        }
        LinkedList<Point> livePoints = new LinkedList<>();
        for (CellEdit edit : pattern.cells()) {
            CellState state = edit.value();
            if (types.idOf(state.type()).filter(ALIVE_ID::equals).isEmpty() || !state.properties().isEmpty()) {
                throw new IOException("Legacy .life supports only plain live cells");
            }

            GridPos position = edit.position();
            checkCoordinate(position.x(), position.y());
            if (position.x() >= pattern.size().width() || position.y() >= pattern.size().height()) {
                throw new IOException("Live cell lies outside the pattern");
            }
            livePoints.add(new Point(position.x(), position.y()));
        }

        ObjectOutputStream output = new ObjectOutputStream(out);
        output.writeObject(livePoints);
        output.flush();
    }

    @Override
    public Pattern load(InputStream in, CellTypeLookup types) throws IOException {
        ObjectInputStream input = new LegacyObjectInputStream(new BoundedInputStream(in));
        Object decoded;
        try {
            decoded = input.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Unsupported class in legacy .life file", e);
        }
        if (!(decoded instanceof LinkedList<?> points) || points.size() > MAX_POINTS) {
            throw new IOException("Expected a bounded list of live points");
        }
        if (points.isEmpty()) {
            return new Pattern(new BoardSize(1, 1), List.of());
        }

        var alive = types.resolve(ALIVE_ID)
                .orElseThrow(() -> new IOException("Unregistered cell type: " + ALIVE_ID));
        var aliveState = new CellState(alive, Map.of());
        List<CellEdit> cells = new ArrayList<>(points.size());
        int width = 1;
        int height = 1;
        for (Object value : points) {
            if (!(value instanceof Point point)) {
                throw new IOException("Expected a live point in legacy .life file");
            }
            checkCoordinate(point.x, point.y);
            cells.add(new CellEdit(new GridPos(point.x, point.y), aliveState));
            width = Math.max(width, point.x + 1);
            height = Math.max(height, point.y + 1);
        }
        return new Pattern(new BoardSize(width, height), cells);
    }

    private static void checkCoordinate(int x, int y) throws IOException {
        if (x < 0 || y < 0 || x >= MAX_SIDE || y >= MAX_SIDE) {
            throw new IOException("Legacy .life coordinate out of range: (" + x + ", " + y + ")");
        }
    }

    /** Reject unexpected serialized classes before loading them. */
    private static final class LegacyObjectInputStream extends ObjectInputStream {
        LegacyObjectInputStream(InputStream source) throws IOException {
            super(source);
            setObjectInputFilter(LegacyObjectInputStream::filter);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException {
            return switch (descriptor.getName()) {
                case "java.util.LinkedList" -> LinkedList.class;
                case "java.awt.Point" -> Point.class;
                default -> throw new InvalidClassException("Unexpected legacy .life class", descriptor.getName());
            };
        }

        @Override
        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException {
            throw new InvalidClassException("Proxy objects are not permitted in legacy .life files");
        }

        private static ObjectInputFilter.Status filter(ObjectInputFilter.FilterInfo info) {
            if (info.depth() > 2 || info.references() > MAX_POINTS + 4L || info.arrayLength() >= 0) {
                return ObjectInputFilter.Status.REJECTED;
            }
            Class<?> type = info.serialClass();
            if (type == null) {
                return ObjectInputFilter.Status.UNDECIDED;
            }
            if ((info.depth() == 1 && type == LinkedList.class)
                    || (info.depth() == 2 && type == Point.class)) {
                return ObjectInputFilter.Status.ALLOWED;
            }
            return ObjectInputFilter.Status.REJECTED;
        }
    }

    /** The object filter is not called for every byte, including null markers. */
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining = MAX_BYTES;

        BoundedInputStream(InputStream source) {
            super(source);
        }

        @Override
        public int read() throws IOException {
            checkRemaining();
            int value = in.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return in.read(buffer, offset, 0);
            }
            checkRemaining();
            int count = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            if (count <= 0) {
                return 0;
            }
            checkRemaining();
            long skipped = in.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }

        private void checkRemaining() throws IOException {
            if (remaining == 0) {
                throw new IOException("Legacy .life file exceeds its size limit");
            }
        }
    }
}
