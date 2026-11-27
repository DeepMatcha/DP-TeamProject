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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Whole simulation as readable UTF-8 JSON. Types are written as their registered IDs in a table;
 * a cell is the table index, or [index, {properties}] when it has properties.
 */
public final class JsonSnapshotCodec implements SnapshotCodec {
    public static final String FORMAT_ID = "codec:json";
    private static final String FORMAT = "life-snapshot";
    private static final long VERSION = 1;

    @Override public String formatId() { return FORMAT_ID; }

    @Override public void save(GameSnapshot snapshot, OutputStream out, CellTypeLookup types) throws IOException {
        BoardState board = snapshot.board();
        Map<CellType, Long> typeIndex = new LinkedHashMap<>();
        List<Object> typeIds = new ArrayList<>();
        List<Object> cells = new ArrayList<>(board.cells().size());
        for (CellState cell : board.cells()) {
            Long index = typeIndex.get(cell.type());
            if (index == null) {
                typeIds.add(types.idOf(cell.type()).orElseThrow(() ->
                        new IOException("Cannot save unregistered cell type: " + cell.type().id())));
                index = (long) typeIndex.size();
                typeIndex.put(cell.type(), index);
            }
            cells.add(cell.properties().isEmpty() ? index : List.of(index, new TreeMap<>(cell.properties())));
        }

        Map<String, Object> boardJson = new LinkedHashMap<>();
        boardJson.put("width", (long) board.size().width());
        boardJson.put("height", (long) board.size().height());
        boardJson.put("generation", board.generation());
        boardJson.put("seed", board.seed());
        boardJson.put("types", typeIds);
        boardJson.put("cells", cells);

        List<Object> plugins = new ArrayList<>();
        for (PluginSelection selection : snapshot.plan().plugins()) {
            Map<String, Object> plugin = new LinkedHashMap<>();
            plugin.put("id", selection.pluginId());
            plugin.put("enabled", selection.enabled());
            plugins.add(plugin);
        }
        Map<String, Object> options = new TreeMap<>();
        snapshot.plan().pluginOptions().forEach((plugin, values) -> options.put(plugin, new TreeMap<>(values)));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("accessor", snapshot.plan().accessorId());
        plan.put("plugins", plugins);
        plan.put("options", options);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("board", boardJson);
        root.put("plan", plan);
        root.put("pluginVersions", new TreeMap<>(snapshot.pluginVersions()));

        out.write(Json.write(root).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override public GameSnapshot load(InputStream in, CellTypeLookup types) throws IOException {
        Object document = Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        try {
            Map<?, ?> root = (Map<?, ?>) document;
            if (!FORMAT.equals(root.get("format")) || !Long.valueOf(VERSION).equals(root.get("version")))
                throw new IOException("Not a version " + VERSION + " " + FORMAT + " document");

            Map<?, ?> boardJson = (Map<?, ?>) root.get("board");
            List<CellType> table = new ArrayList<>();
            for (Object id : (List<?>) boardJson.get("types"))
                table.add(types.resolve((String) id).orElseThrow(() ->
                        new IOException("Unregistered cell type: " + id)));
            List<CellState> cells = new ArrayList<>();
            for (Object cell : (List<?>) boardJson.get("cells")) {
                if (cell instanceof List<?> withProperties)
                    cells.add(new CellState(table.get(Math.toIntExact((Long) withProperties.get(0))),
                            strings((Map<?, ?>) withProperties.get(1))));
                else
                    cells.add(new CellState(table.get(Math.toIntExact((Long) cell)), Map.of()));
            }
            BoardState board = new BoardState(
                    new BoardSize(Math.toIntExact((Long) boardJson.get("width")), Math.toIntExact((Long) boardJson.get("height"))),
                    (Long) boardJson.get("generation"), (Long) boardJson.get("seed"), cells);

            Map<?, ?> planJson = (Map<?, ?>) root.get("plan");
            List<PluginSelection> plugins = new ArrayList<>();
            for (Object plugin : (List<?>) planJson.get("plugins")) {
                Map<?, ?> selection = (Map<?, ?>) plugin;
                plugins.add(new PluginSelection((String) selection.get("id"), (Boolean) selection.get("enabled")));
            }
            Map<String, Map<String, String>> options = new LinkedHashMap<>();
            for (var entry : ((Map<?, ?>) planJson.get("options")).entrySet())
                options.put((String) entry.getKey(), strings((Map<?, ?>) entry.getValue()));
            SimulationPlan plan = new SimulationPlan((String) planJson.get("accessor"), plugins, options);

            return new GameSnapshot(board, plan, strings((Map<?, ?>) root.get("pluginVersions")));
        } catch (RuntimeException malformed) {
            throw new IOException("Malformed " + FORMAT + " document", malformed);
        }
    }

    private static Map<String, String> strings(Map<?, ?> json) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : json.entrySet()) result.put((String) entry.getKey(), (String) entry.getValue());
        return result;
    }
}
