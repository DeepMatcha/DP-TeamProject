import com.holub.life.api.*;
import java.util.*;

/** Compile-only Phase 2 example. Does not depend on host classes or core singletons. */
public class PluginCompileProbe implements LifePlugin {
    private static final class DemoType extends CellType {
        private static final DemoType INSTANCE = new DemoType();
        private DemoType() { super("demo:aging-cell"); }
        @Override public boolean isAlive(CellState state) { return true; }
    }
    @Override public String id() { return "demo:aging"; }
    @Override public void register(PluginRegistrar registrar) {
        registrar.registerCellType(DemoType.INSTANCE);
        registrar.registerLifecycle(new LifecycleManager(1) {
            @Override public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                CellState current = neighbors.center().state();
                // This example chooses to preserve other types. The API does not require it.
                if (current.type() != DemoType.INSTANCE) return current;
                int age = Integer.parseInt(current.properties().getOrDefault("demo:age", "0"));
                var updated = new HashMap<>(current.properties());
                updated.put("demo:age", Integer.toString(Math.addExact(age, 1)));
                return new CellState(current.type(), updated);
            }
        });
    }
}
