package com.holub.life.api;
import java.util.Optional;
/** Internal loading/registration lookup, NOT a required public CellTypes collection. */
public interface CellTypeLookup {
    Optional<CellType> resolve(String typeId);
}
