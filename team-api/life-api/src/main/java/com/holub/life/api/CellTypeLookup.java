package com.holub.life.api;
import java.util.Optional;
/** Read-only identity mapping. Resolves IDs to already registered singleton objects. */
public interface CellTypeLookup {
    Optional<CellType> resolve(String typeId);
    /** Empty for an unregistered object, even if another object has the same id(). */
    Optional<String> idOf(CellType type);
}
