package com.holub.life.api;
import java.util.Objects;
/** Index is zero-based registration order within one plugin and one API point. */
public record RegistrationInfo(String pluginId, ApiPoint point, String id, int index) {
    public RegistrationInfo {
        Objects.requireNonNull(pluginId);
        Objects.requireNonNull(point);
        Objects.requireNonNull(id);
        if (pluginId.isBlank() || id.isBlank() || index < 0)
            throw new IllegalArgumentException("Invalid registration metadata");
    }
}
