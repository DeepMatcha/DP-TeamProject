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
    /**
     * Declares that this plugin takes over the host's built-in module of a point:
     * {@link ApiPoint#LIFECYCLE} is the basic Conway stage, {@link ApiPoint#VIEW} the built-in window.
     * While this plugin is enabled in the plan, the host leaves that built-in module off.
     * Other points have no built-in module and are rejected with IllegalArgumentException.
     */
    void replaceDefault(ApiPoint point);
}
