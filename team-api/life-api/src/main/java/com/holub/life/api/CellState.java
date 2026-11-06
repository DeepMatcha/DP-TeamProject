package com.holub.life.api;
import java.util.Map;
import java.util.Objects;
/** Data is per board coordinate, NEVER stored mutably on a singleton CellType. */
public record CellState(CellType type, Map<String,String> properties) {
    public CellState {
        Objects.requireNonNull(type);
        properties = Map.copyOf(Objects.requireNonNull(properties));
    }
}
