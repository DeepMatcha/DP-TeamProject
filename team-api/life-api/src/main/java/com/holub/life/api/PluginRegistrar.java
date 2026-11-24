package com.holub.life.api;
/** Host-scoped to one plugin. Each registerLifecycle call appends one whole-board stage. */
public interface PluginRegistrar {
    void registerCellType(CellType type);
    void registerAccessor(AccessorFactory<GridPos> accessor);
    void registerLifecycle(LifecycleManager manager);
    void registerInputTool(InputTool tool);
    void registerSnapshotCodec(SnapshotCodec codec);
    void registerPatternCodec(PatternCodec codec);
    void registerView(ViewPlugin view);
    /**
     * Read-only live view of the registered types, usable for the host's lifetime.
     * The core types of {@link CoreCellTypes} are registered before any plugin's register().
     */
    CellTypeLookup cellTypes();
}
