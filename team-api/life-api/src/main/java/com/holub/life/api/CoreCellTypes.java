package com.holub.life.api;

/**
 * Stable IDs of the two host-owned core types. Only the ID strings are shared;
 * the singleton objects still belong to the host and are obtained by lookup.
 */
public final class CoreCellTypes {
    public static final String EMPTY_ID = "core:empty";
    public static final String ALIVE_ID = "core:alive";

    private CoreCellTypes() {}

    public static CellType empty(CellTypeLookup types) { return require(types, EMPTY_ID); }
    public static CellType alive(CellTypeLookup types) { return require(types, ALIVE_ID); }

    private static CellType require(CellTypeLookup types, String id) {
        return types.resolve(id).orElseThrow(() ->
                new IllegalStateException("Host has not registered core type " + id));
    }
}
