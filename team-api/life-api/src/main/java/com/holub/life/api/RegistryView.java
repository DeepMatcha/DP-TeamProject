package com.holub.life.api;
import java.util.List;
import java.util.Optional;
/**
 * Read-only catalog of loaded modules, including disabled plugins.
 * Returned lists are immutable; there is no registration/mutation operation here.
 */
public interface RegistryView extends CellTypeLookup {
    List<PluginInfo> plugins();
    List<RegistrationInfo> registrations();
    Optional<AccessorFactory<GridPos>> accessor(String id);
    Optional<InputTool> inputTool(String id);
    Optional<SnapshotCodec> snapshotCodec(String formatId);
    Optional<PatternCodec> patternCodec(String formatId);
    Optional<ViewPlugin> view(String id);
}
