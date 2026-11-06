import com.holub.life.api.*;
import java.util.*;

// API-only consumer example; no reference to com.holub.life.Resident or Universe.
public class PluginCompileProbe implements LifePlugin {
    public static final CellType ALIVE = new CellType("core:alive") {
        @Override public boolean isAlive(CellState state) { return true; }
    };
    public static final CellType EMPTY = new CellType("core:empty") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    @Override public String id() { return "demo:aging"; }
    @Override public void register(PluginRegistrar registrar) {
        registrar.registerLifecycle(ALIVE, new LifecycleManager(1) {
            @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                CellState current = neighbors.center().state();
                int age = Integer.parseInt(current.properties().getOrDefault("demo:age","0"));
                var updated = new java.util.HashMap<>(current.properties());
                updated.put("demo:age", Integer.toString(age + 1));
                return new CellState(current.type(), updated);
            }
        });
    }
}
