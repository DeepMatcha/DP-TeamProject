package com.holub.life.testkit;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.PatternCodec;
import com.holub.life.api.PluginInfo;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.api.RegistryView;
import com.holub.life.api.SnapshotCodec;
import com.holub.life.api.StepContext;
import com.holub.life.api.ViewPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;

/**
 * Minimal stand-in for the Phase 1 host: registry plus whole-board stage evaluation.
 * It has no Conway stage or window of its own; only what loaded plugins register is run,
 * and a replaceDefault declaration is only recorded.
 */
public final class FakeHost implements RegistryView {
    public static final String BOUNDED_ACCESSOR = "core:bounded";
    public static final CellType EMPTY = new CoreType(CoreCellTypes.EMPTY_ID, false);
    public static final CellType ALIVE = new CoreType(CoreCellTypes.ALIVE_ID, true);
    public static final CellState EMPTY_STATE = new CellState(EMPTY, Map.of());
    public static final CellState ALIVE_STATE = new CellState(ALIVE, Map.of());

    private static final class CoreType extends CellType {
        private final boolean alive;
        private CoreType(String id, boolean alive) { super(id); this.alive = alive; }
        @Override public boolean isAlive(CellState state) { return alive; }
    }

    private final Map<String, CellType> types = new LinkedHashMap<>();
    private final Map<String, AccessorFactory<GridPos>> accessors = new LinkedHashMap<>();
    private final Map<String, InputTool> tools = new LinkedHashMap<>();
    private final Map<String, SnapshotCodec> snapshotCodecs = new LinkedHashMap<>();
    private final Map<String, PatternCodec> patternCodecs = new LinkedHashMap<>();
    private final Map<String, ViewPlugin> views = new LinkedHashMap<>();
    private final List<LifecycleManager> lifecycles = new ArrayList<>();
    private final List<PluginInfo> plugins = new ArrayList<>();
    private final List<RegistrationInfo> registrations = new ArrayList<>();
    private final Map<ApiPoint, List<String>> replacedDefaults = new EnumMap<>(ApiPoint.class);

    public FakeHost() {
        types.put(EMPTY.id(), EMPTY);
        types.put(ALIVE.id(), ALIVE);
        accessors.put(BOUNDED_ACCESSOR, new AccessorFactory<>() {
            @Override public String id() { return BOUNDED_ACCESSOR; }
            @Override public Class<GridPos> coordinateType() { return GridPos.class; }
            @Override public Accessor<GridPos> bind(BoardState snapshot) {
                return pos -> pos.x() < 0 || pos.y() < 0
                        || pos.x() >= snapshot.size().width() || pos.y() >= snapshot.size().height()
                        ? Optional.empty()
                        : Optional.of(new CellSample(pos, snapshot.cellAt(pos)));
            }
        });
    }

    /** Runs the plugin's register() against a registrar scoped to it. */
    public FakeHost load(LifePlugin plugin) {
        plugins.add(new PluginInfo(plugin.id(), Map.of()));
        plugin.register(new Registrar(plugin.id()));
        return this;
    }

    public List<LifecycleManager> lifecycles() { return List.copyOf(lifecycles); }

    /** IDs of the plugins that declared they take over the built-in module of the point. */
    public List<String> replacingDefault(ApiPoint point) {
        return List.copyOf(replacedDefaults.getOrDefault(point, List.of()));
    }

    /** One whole tick with the bounded (non-wrapping) accessor. */
    public BoardState step(BoardState source) { return step(source, BOUNDED_ACCESSOR); }

    /** One whole tick: every registered stage in order, then generation + 1. */
    public BoardState step(BoardState source, String accessorId) {
        AccessorFactory<GridPos> factory = accessor(accessorId).orElseThrow();
        BoardState stage = source;
        for (int index = 0; index < lifecycles.size(); index++)
            stage = runStage(lifecycles.get(index), stage, factory, index);
        return new BoardState(stage.size(), source.generation() + 1, source.seed(), stage.cells());
    }

    /** Evaluates one rule on every cell of the same input snapshot. Generation is left unchanged. */
    public static BoardState runStage(LifecycleManager rule, BoardState input,
                                      AccessorFactory<GridPos> factory, int stageIndex) {
        Accessor<GridPos> accessor = factory.bind(input);
        List<CellState> next = new ArrayList<>(input.cells().size());
        for (int y = 0; y < input.size().height(); y++)
            for (int x = 0; x < input.size().width(); x++) {
                var neighbors = new FakeNeighborBoard(input, accessor, new GridPos(x, y), rule.distance());
                var context = new StepContext(input.generation(), randomFor(input, stageIndex, x, y));
                next.add(Objects.requireNonNull(rule.figureNextState(neighbors, context)));
            }
        return new BoardState(input.size(), input.generation(), input.seed(), next);
    }

    /** Derived only from seed, generation, stage and position, so recomputation gives the same draws. */
    private static SplittableRandom randomFor(BoardState board, int stageIndex, int x, int y) {
        long mixed = board.seed();
        for (long part : new long[] {board.generation(), stageIndex, x, y})
            mixed = new SplittableRandom(mixed ^ part).nextLong();
        return new SplittableRandom(mixed);
    }

    @Override public Optional<CellType> resolve(String typeId) { return Optional.ofNullable(types.get(typeId)); }
    @Override public Optional<String> idOf(CellType type) {
        return types.get(type.id()) == type ? Optional.of(type.id()) : Optional.empty();
    }
    @Override public List<PluginInfo> plugins() { return List.copyOf(plugins); }
    @Override public List<RegistrationInfo> registrations() { return List.copyOf(registrations); }
    @Override public Optional<AccessorFactory<GridPos>> accessor(String id) { return Optional.ofNullable(accessors.get(id)); }
    @Override public Optional<InputTool> inputTool(String id) { return Optional.ofNullable(tools.get(id)); }
    @Override public Optional<SnapshotCodec> snapshotCodec(String formatId) { return Optional.ofNullable(snapshotCodecs.get(formatId)); }
    @Override public Optional<PatternCodec> patternCodec(String formatId) { return Optional.ofNullable(patternCodecs.get(formatId)); }
    @Override public Optional<ViewPlugin> view(String id) { return Optional.ofNullable(views.get(id)); }

    private final class Registrar implements PluginRegistrar {
        private final String pluginId;
        private final Map<ApiPoint, Integer> counts = new EnumMap<>(ApiPoint.class);

        private Registrar(String pluginId) { this.pluginId = pluginId; }

        private <T> void add(ApiPoint point, Map<String, T> target, String id, T module) {
            if (target.putIfAbsent(id, module) != null)
                throw new IllegalArgumentException("Duplicate " + point + " id: " + id);
            record(point, id);
        }

        private void record(ApiPoint point, String id) {
            int index = counts.merge(point, 1, Integer::sum) - 1;
            registrations.add(new RegistrationInfo(pluginId, point, id, index));
        }

        @Override public void registerCellType(CellType type) { add(ApiPoint.CELL_TYPE, types, type.id(), type); }
        @Override public void registerAccessor(AccessorFactory<GridPos> accessor) { add(ApiPoint.ACCESSOR, accessors, accessor.id(), accessor); }
        @Override public void registerLifecycle(LifecycleManager manager) {
            lifecycles.add(Objects.requireNonNull(manager));
            record(ApiPoint.LIFECYCLE, pluginId + "#lifecycle/" + counts.getOrDefault(ApiPoint.LIFECYCLE, 0));
        }
        @Override public void registerInputTool(InputTool tool) { add(ApiPoint.INPUT_TOOL, tools, tool.id(), tool); }
        @Override public void registerSnapshotCodec(SnapshotCodec codec) { add(ApiPoint.SNAPSHOT_CODEC, snapshotCodecs, codec.formatId(), codec); }
        @Override public void registerPatternCodec(PatternCodec codec) { add(ApiPoint.PATTERN_CODEC, patternCodecs, codec.formatId(), codec); }
        @Override public void registerView(ViewPlugin view) { add(ApiPoint.VIEW, views, view.id(), view); }
        @Override public CellTypeLookup cellTypes() { return FakeHost.this; }
        @Override public void replaceDefault(ApiPoint point) {
            if (point != ApiPoint.LIFECYCLE && point != ApiPoint.VIEW)
                throw new IllegalArgumentException("No built-in module for " + point);
            replacedDefaults.computeIfAbsent(point, key -> new ArrayList<>()).add(pluginId);
        }
    }
}
