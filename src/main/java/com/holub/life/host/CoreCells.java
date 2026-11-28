package com.holub.life.host;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CoreCellTypes;

/** Singleton types owned by the default host. */
public final class CoreCells {
    public static final CellType EMPTY = Empty.INSTANCE;
    public static final CellType ALIVE = Alive.INSTANCE;

    private CoreCells() {}

    private static final class Empty extends CellType {
        static final Empty INSTANCE = new Empty();

        private Empty() {
            super(CoreCellTypes.EMPTY_ID);
        }

        @Override
        public boolean isAlive(CellState state) {
            return false;
        }
    }

    private static final class Alive extends CellType {
        static final Alive INSTANCE = new Alive();

        private Alive() {
            super(CoreCellTypes.ALIVE_ID);
        }

        @Override
        public boolean isAlive(CellState state) {
            return true;
        }
    }
}
