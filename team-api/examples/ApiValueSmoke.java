import com.holub.life.api.*;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Dependency-free DTO/signature checks only, NOT JUnit or a game-engine integration test. */
public final class ApiValueSmoke {
    private static int checks;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        checks++;
    }
    private static void rejects(Runnable operation, Class<? extends Throwable> type, String name) {
        try { operation.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) { checks++; return; }
            throw new AssertionError(name + ": wrong exception", error);
        }
        throw new AssertionError(name + ": accepted invalid operation");
    }
    private static final CellType LIVE = new CellType("test:live") {
        public boolean isAlive(CellState state) { return true; }
    };
    public static void main(String[] args) throws Exception {
        Map<String,String> properties = new HashMap<>();
        properties.put("age", "1");
        CellState first = new CellState(LIVE, properties);
        properties.put("age", "999");
        check(first.properties().get("age").equals("1"), "CellState defensive copy");
        rejects(() -> first.properties().put("age", "3"), UnsupportedOperationException.class, "immutable properties");
        CellState second = new CellState(LIVE, Map.of("age", "8"));
        check(first.type() == second.type() && !first.properties().equals(second.properties()), "singleton type, distinct state");
        List<CellState> cells = new ArrayList<>(List.of(first, second));
        BoardState board = new BoardState(new BoardSize(2,1), 4, 42, cells);
        cells.clear();
        check(board.cellAt(new GridPos(1,0)).equals(second), "BoardState defensive copy");
        rejects(() -> board.cells().clear(), UnsupportedOperationException.class, "immutable board");
        rejects(() -> board.cellAt(new GridPos(-1,0)), IndexOutOfBoundsException.class, "canonical bounds");
        SimulationPlan plan = new SimulationPlan("test:bounded", List.of(
            new PluginSelection("B", true), new PluginSelection("A", true)), Map.of("A", Map.of("mode", "x")));
        check(plan.plugins().get(0).pluginId().equals("B"), "list position is execution order");
        rejects(() -> plan.pluginOptions().get("A").put("mode", "y"), UnsupportedOperationException.class, "deep immutable options");
        rejects(() -> new SimulationPlan("test:bounded", List.of(
            new PluginSelection("A", true), new PluginSelection("A", false)), Map.of()),
            IllegalArgumentException.class, "duplicate plugin rejected");
        rejects(() -> new PlaybackState(false, 0), IllegalArgumentException.class, "positive interval");
        StepContext context = new StepContext(4, new Random(42));
        check(context.random().nextLong() == new Random(42).nextLong(), "provided generator is available");
        rejects(() -> new StepContext(0, null), NullPointerException.class, "non-null generator");
        List<CellEdit> edits = new ArrayList<>(List.of(new CellEdit(new GridPos(0,0), first),
            new CellEdit(new GridPos(0,0), second)));
        BoardPatch patch = new BoardPatch(edits);
        edits.clear();
        check(patch.edits().size() == 2 && patch.edits().get(1).value().equals(second), "patch preserves edit order");
        check(PluginRegistrar.class.getMethod("registerLifecycle", LifecycleManager.class) != null,
            "one lifecycle argument");
        check(Arrays.stream(PluginRegistrar.class.getMethods()).noneMatch(m -> m.getName().equals("registerBoardEffect")),
            "no BoardEffect registration");
        String[] operations = {"step", "apply", "setRunning", "setTickIntervalMillis", "playbackState", "session", "restore", "configure"};
        for (String name : operations) {
            check(Arrays.stream(ControllerPort.class.getMethods()).anyMatch(m -> m.getName().equals(name)
                && m.getReturnType() == CompletionStage.class), "completion contract: " + name);
        }
        CellType other = new CellType("test:live") { public boolean isAlive(CellState state) { return true; } };
        CellTypeLookup lookup = new CellTypeLookup() {
            public Optional<CellType> resolve(String id) { return id.equals(LIVE.id()) ? Optional.of(LIVE) : Optional.empty(); }
            public Optional<String> idOf(CellType type) { return type == LIVE ? Optional.of(LIVE.id()) : Optional.empty(); }
        };
        check(lookup.resolve(lookup.idOf(LIVE).orElseThrow()).orElseThrow() == LIVE, "identity round trip");
        check(lookup.idOf(other).isEmpty(), "same id is not registered singleton");
        check(PluginRegistrar.class.getMethod("cellTypes").getReturnType() == CellTypeLookup.class,
            "plugins can look up registered types");
        check(PluginRegistrar.class.getMethod("replaceDefault", ApiPoint.class) != null,
            "plugins can take over a built-in module");
        rejects(() -> CoreCellTypes.empty(lookup), IllegalStateException.class, "missing core type is reported");
        System.out.println("PASS: " + checks + " API value/signature checks (not host/JUnit validation)");
    }
}
