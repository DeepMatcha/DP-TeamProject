package com.holub.life.api;
import java.util.Map;
import java.util.Objects;
public record ToolInput(GridPos anchor, String action, Map<String,String> options) {
    public ToolInput {
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(action);
        options = Map.copyOf(Objects.requireNonNull(options));
    }
}
