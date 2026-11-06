import com.holub.life.api.*;
import java.util.*;

// Host-side API-only placeholder. It deliberately does NOT depend on plugin code.
public class HostCompileProbe {
    private static final CellType LOCAL_EMPTY = new CellType("core:empty") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    GameEngine engine = (source, plan) -> source; // compile probe only; not a real engine

    public BoardState sample() {
        var cells = Collections.nCopies(4, new CellState(LOCAL_EMPTY, Map.of()));
        return new BoardState(new BoardSize(2,2), 0L, 42L, cells);
    }
}
