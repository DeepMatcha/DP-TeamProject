package com.holub.life.plugin.codec;

import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.SnapshotCodec;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Whole simulation as compact binary. Each distinct cell state (type ID + properties) is written
 * once in a table, and the board is one table index per cell (one byte while the table is small). Reads exactly the bytes it wrote,
 * so other data may follow in the same stream.
 */
public final class BinarySnapshotCodec implements SnapshotCodec {
    public static final String FORMAT_ID = "codec:binary";
    private static final int MAGIC = 0x4C494653; // "LIFS"
    private static final int VERSION = 1;

    @Override public String formatId() { return FORMAT_ID; }

    @Override public void save(GameSnapshot snapshot, OutputStream stream, CellTypeLookup types) throws IOException {
        BoardState board = snapshot.board();
        Map<CellState, Integer> stateIndex = new LinkedHashMap<>();
        for (CellState cell : board.cells()) stateIndex.putIfAbsent(cell, stateIndex.size());

        DataOutputStream out = new DataOutputStream(stream);
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        out.writeInt(board.size().width());
        out.writeInt(board.size().height());
        out.writeLong(board.generation());
        out.writeLong(board.seed());

        out.writeInt(stateIndex.size());
        for (CellState state : stateIndex.keySet()) {
            out.writeUTF(types.idOf(state.type()).orElseThrow(() ->
                    new IOException("Cannot save unregistered cell type: " + state.type().id())));
            writeStrings(out, state.properties());
        }
        for (CellState cell : board.cells()) writeIndex(out, stateIndex.get(cell));

        out.writeUTF(snapshot.plan().accessorId());
        out.writeInt(snapshot.plan().plugins().size());
        for (PluginSelection selection : snapshot.plan().plugins()) {
            out.writeUTF(selection.pluginId());
            out.writeBoolean(selection.enabled());
        }
        Map<String, Map<String, String>> options = new TreeMap<>(snapshot.plan().pluginOptions());
        out.writeInt(options.size());
        for (var entry : options.entrySet()) {
            out.writeUTF(entry.getKey());
            writeStrings(out, entry.getValue());
        }
        writeStrings(out, snapshot.pluginVersions());
        out.flush();
    }

    @Override public GameSnapshot load(InputStream stream, CellTypeLookup types) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        if (in.readInt() != MAGIC) throw new IOException("Not a binary life snapshot");
        int version = in.readInt();
        if (version != VERSION) throw new IOException("Unsupported binary snapshot version " + version);
        try {
            BoardSize size = new BoardSize(in.readInt(), in.readInt());
            long generation = in.readLong();
            long seed = in.readLong();

            List<CellState> table = new ArrayList<>();
            for (int count = count(in, size.area()); count > 0; count--) {
                String id = in.readUTF();
                CellType type = types.resolve(id).orElseThrow(() -> new IOException("Unregistered cell type: " + id));
                table.add(new CellState(type, readStrings(in)));
            }
            List<CellState> cells = new ArrayList<>(size.area());
            for (int cell = 0; cell < size.area(); cell++) cells.add(table.get(readIndex(in)));
            BoardState board = new BoardState(size, generation, seed, cells);

            String accessorId = in.readUTF();
            List<PluginSelection> plugins = new ArrayList<>();
            for (int count = count(in, Short.MAX_VALUE); count > 0; count--)
                plugins.add(new PluginSelection(in.readUTF(), in.readBoolean()));
            Map<String, Map<String, String>> options = new LinkedHashMap<>();
            for (int count = count(in, Short.MAX_VALUE); count > 0; count--) options.put(in.readUTF(), readStrings(in));
            return new GameSnapshot(board, new SimulationPlan(accessorId, plugins, options), readStrings(in));
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed binary life snapshot", malformed);
        }
    }

    private static void writeStrings(DataOutputStream out, Map<String, String> strings) throws IOException {
        out.writeInt(strings.size());
        for (var entry : new TreeMap<>(strings).entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
    }

    private static Map<String, String> readStrings(DataInputStream in) throws IOException {
        Map<String, String> strings = new LinkedHashMap<>();
        for (int count = count(in, Short.MAX_VALUE); count > 0; count--) strings.put(in.readUTF(), in.readUTF());
        return strings;
    }

    /** Table index in 7-bit groups, low group first; the high bit marks that another group follows. */
    private static void writeIndex(DataOutputStream out, int index) throws IOException {
        while (index >= 0x80) {
            out.writeByte(index & 0x7F | 0x80);
            index >>>= 7;
        }
        out.writeByte(index);
    }

    private static int readIndex(DataInputStream in) throws IOException {
        int index = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int group = in.readUnsignedByte();
            index |= (group & 0x7F) << shift;
            if (group < 0x80) return index;
        }
        throw new IOException("Corrupted cell index");
    }

    /** A corrupted count must fail instead of driving a huge loop. */
    private static int count(DataInputStream in, int limit) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > limit) throw new IOException("Corrupted count " + count);
        return count;
    }
}
