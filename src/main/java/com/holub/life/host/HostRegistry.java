package com.holub.life.host;

import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
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
import com.holub.life.api.ViewPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The host's immutable catalog of the modules registered at startup. */
public final class HostRegistry implements RegistryView {
    private final List<PluginInfo> plugins;
    private final List<RegistrationInfo> registrations;
    private final CellTypeLookup cellTypes;
    private final Map<String, AccessorFactory<GridPos>> accessors;
    private final Map<String, InputTool> inputTools;
    private final Map<String, SnapshotCodec> snapshotCodecs;
    private final Map<String, PatternCodec> patternCodecs;
    private final Map<String, ViewPlugin> views;
    private final Map<String, List<LifecycleManager>> lifecycleStages;
    private final Map<String, String> pluginVersions;
    private final Map<String, Set<ApiPoint>> defaultReplacements;

    public HostRegistry(List<? extends LifePlugin> plugins) {
        this(plugins, Map.of());
    }

    public HostRegistry(List<? extends LifePlugin> plugins,
                        Map<String, Map<String, String>> metadataByPluginId) {
        Objects.requireNonNull(metadataByPluginId, "metadataByPluginId");
        var builder = new RegistryBuilder();
        var knownIds = new HashSet<String>();
        var catalog = new ArrayList<PluginInfo>();
        var versions = new HashMap<String, String>();

        for (LifePlugin plugin : List.copyOf(plugins)) {
            String pluginId = Objects.requireNonNull(plugin.id(), "plugin.id()");
            var info = new PluginInfo(pluginId, metadataByPluginId.getOrDefault(pluginId, Map.of()));
            if (!knownIds.add(pluginId)) {
                throw new IllegalArgumentException("Duplicate plugin ID: " + pluginId);
            }
            catalog.add(info);
            String version = info.metadata().get("version");
            if (version != null) {
                versions.put(pluginId, version);
            }

            var registrar = new Registrar(pluginId, builder);
            try {
                plugin.register(registrar);
                registrar.validateDefaultReplacements();
                builder.lifecycleStages.put(pluginId, List.copyOf(registrar.stages));
                builder.defaultReplacements.put(pluginId, Set.copyOf(registrar.defaultReplacements));
            } finally {
                registrar.close();
            }
        }

        this.plugins = List.copyOf(catalog);
        registrations = List.copyOf(builder.registrations);
        cellTypes = builder.cellTypes;
        accessors = Map.copyOf(builder.accessors);
        inputTools = Map.copyOf(builder.inputTools);
        snapshotCodecs = Map.copyOf(builder.snapshotCodecs);
        patternCodecs = Map.copyOf(builder.patternCodecs);
        views = Map.copyOf(builder.views);
        lifecycleStages = Map.copyOf(builder.lifecycleStages);
        pluginVersions = Map.copyOf(versions);
        defaultReplacements = Map.copyOf(builder.defaultReplacements);
    }

    @Override
    public List<PluginInfo> plugins() {
        return plugins;
    }

    @Override
    public List<RegistrationInfo> registrations() {
        return registrations;
    }

    @Override
    public Optional<CellType> resolve(String typeId) {
        return cellTypes.resolve(typeId);
    }

    @Override
    public Optional<String> idOf(CellType type) {
        return cellTypes.idOf(type);
    }

    @Override
    public Optional<AccessorFactory<GridPos>> accessor(String id) {
        return lookup(accessors, id);
    }

    @Override
    public Optional<InputTool> inputTool(String id) {
        return lookup(inputTools, id);
    }

    @Override
    public Optional<SnapshotCodec> snapshotCodec(String formatId) {
        return lookup(snapshotCodecs, formatId);
    }

    @Override
    public Optional<PatternCodec> patternCodec(String formatId) {
        return lookup(patternCodecs, formatId);
    }

    @Override
    public Optional<ViewPlugin> view(String id) {
        return lookup(views, id);
    }

    List<LifecycleManager> lifecycleStages(String pluginId) {
        return lifecycleStages.getOrDefault(pluginId, List.of());
    }

    Map<String, String> pluginVersions() {
        return pluginVersions;
    }

    boolean replacesDefault(String pluginId, ApiPoint point) {
        return defaultReplacements.getOrDefault(pluginId, Set.of()).contains(point);
    }

    private static <T> Optional<T> lookup(Map<String, T> modules, String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(modules.get(id));
    }

    /** The lookup may outlive the registration callback; only the registrar can add entries. */
    private static final class RegisteredCellTypes implements CellTypeLookup {
        private final Map<String, CellType> types = new ConcurrentHashMap<>(Map.of(
                CoreCellTypes.EMPTY_ID, CoreCells.EMPTY,
                CoreCellTypes.ALIVE_ID, CoreCells.ALIVE));

        @Override
        public Optional<CellType> resolve(String typeId) {
            return lookup(types, typeId);
        }

        @Override
        public Optional<String> idOf(CellType type) {
            if (type != null && types.get(type.id()) == type) {
                return Optional.of(type.id());
            }
            return Optional.empty();
        }
    }

    private static final class RegistryBuilder {
        final List<RegistrationInfo> registrations = new ArrayList<>();
        final RegisteredCellTypes cellTypes = new RegisteredCellTypes();
        final Map<String, AccessorFactory<GridPos>> accessors = new HashMap<>();
        final Map<String, InputTool> inputTools = new HashMap<>();
        final Map<String, SnapshotCodec> snapshotCodecs = new HashMap<>();
        final Map<String, PatternCodec> patternCodecs = new HashMap<>();
        final Map<String, ViewPlugin> views = new HashMap<>();
        final Map<String, List<LifecycleManager>> lifecycleStages = new HashMap<>();
        final Map<String, Set<ApiPoint>> defaultReplacements = new HashMap<>();
    }

    private static final class Registrar implements PluginRegistrar {
        private final String pluginId;
        private final Map<ApiPoint, Integer> nextIndex = new EnumMap<>(ApiPoint.class);
        private final List<LifecycleManager> stages = new ArrayList<>();
        private final Set<ApiPoint> defaultReplacements = EnumSet.noneOf(ApiPoint.class);
        private RegistryBuilder builder;

        Registrar(String pluginId, RegistryBuilder builder) {
            this.pluginId = pluginId;
            this.builder = builder;
        }

        void close() {
            builder = null;
        }

        private RegistryBuilder activeBuilder() {
            if (builder == null) {
                throw new IllegalStateException("Registration has ended for plugin: " + pluginId);
            }
            return builder;
        }

        private <T> void register(ApiPoint point, String id, T module, Map<String, T> modules) {
            int index = nextIndex.getOrDefault(point, 0);
            var info = new RegistrationInfo(pluginId, point, id, index);
            if (modules.putIfAbsent(id, module) != null) {
                throw new IllegalArgumentException("Duplicate " + point + " ID: " + id);
            }
            nextIndex.put(point, index + 1);
            activeBuilder().registrations.add(info);
        }

        @Override
        public void registerCellType(CellType type) {
            register(ApiPoint.CELL_TYPE, type.id(), type, activeBuilder().cellTypes.types);
        }

        @Override
        public void registerAccessor(AccessorFactory<GridPos> accessor) {
            register(ApiPoint.ACCESSOR, accessor.id(), accessor, activeBuilder().accessors);
        }

        @Override
        public void registerLifecycle(LifecycleManager manager) {
            var active = activeBuilder();
            int index = stages.size();
            stages.add(Objects.requireNonNull(manager, "manager"));
            active.registrations.add(new RegistrationInfo(
                    pluginId, ApiPoint.LIFECYCLE, pluginId + "#lifecycle/" + index, index));
        }

        @Override
        public void registerInputTool(InputTool tool) {
            register(ApiPoint.INPUT_TOOL, tool.id(), tool, activeBuilder().inputTools);
        }

        @Override
        public void registerSnapshotCodec(SnapshotCodec codec) {
            register(ApiPoint.SNAPSHOT_CODEC, codec.formatId(), codec, activeBuilder().snapshotCodecs);
        }

        @Override
        public void registerPatternCodec(PatternCodec codec) {
            register(ApiPoint.PATTERN_CODEC, codec.formatId(), codec, activeBuilder().patternCodecs);
        }

        @Override
        public void registerView(ViewPlugin view) {
            register(ApiPoint.VIEW, view.id(), view, activeBuilder().views);
        }

        @Override
        public CellTypeLookup cellTypes() {
            return activeBuilder().cellTypes;
        }

        @Override
        public void replaceDefault(ApiPoint point) {
            activeBuilder();
            if (point != ApiPoint.LIFECYCLE && point != ApiPoint.VIEW) {
                throw new IllegalArgumentException("No built-in module for " + point);
            }
            defaultReplacements.add(point);
        }

        private void validateDefaultReplacements() {
            if (defaultReplacements.contains(ApiPoint.LIFECYCLE) && stages.isEmpty()) {
                throw new IllegalArgumentException("Missing replacement lifecycle for " + pluginId);
            }
            if (defaultReplacements.contains(ApiPoint.VIEW) && !nextIndex.containsKey(ApiPoint.VIEW)) {
                throw new IllegalArgumentException("Missing replacement view for " + pluginId);
            }
        }
    }
}
