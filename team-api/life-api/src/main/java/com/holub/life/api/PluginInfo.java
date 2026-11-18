package com.holub.life.api;
import java.util.Map;
import java.util.Objects;
/** Host loading metadata; version is metadata.get("version"), not a plugin method. */
public record PluginInfo(String id, Map<String,String> metadata) {
    public PluginInfo {
        Objects.requireNonNull(id);
        if (id.isBlank()) throw new IllegalArgumentException("Blank plugin id");
        metadata = Map.copyOf(Objects.requireNonNull(metadata));
    }
}
