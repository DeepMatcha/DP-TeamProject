package com.holub.life.api;
/** Registrar is scoped to one plugin; host knows its pluginId. */
public interface PluginRegistrar {
    void registerCellType(CellType type);
    void registerAccessor(AccessorFactory<GridPos> accessor);
    void registerLifecycle(CellType type, LifecycleManager manager);
    void registerBoardEffect(BoardEffect effect);
    void registerInputTool(InputTool tool);
    void registerSnapshotCodec(SnapshotCodec codec);
    void registerPatternCodec(PatternCodec codec);
    void registerView(ViewPlugin view);
}
