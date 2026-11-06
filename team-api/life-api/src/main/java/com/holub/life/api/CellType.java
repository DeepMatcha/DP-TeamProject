package com.holub.life.api;
import java.util.Objects;
/** Type descriptor. Each concrete type may expose a static final INSTANCE. */
public abstract class CellType {
    private final String id;
    protected CellType(String id) {
        this.id = Objects.requireNonNull(id);
        if (id.isBlank()) throw new IllegalArgumentException("Empty type id");
    }
    public final String id() { return id; }
    public abstract boolean isAlive(CellState state);
}
