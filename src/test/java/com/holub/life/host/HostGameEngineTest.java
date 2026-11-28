package com.holub.life.host;

import static com.holub.life.host.fixture.FakePhase2Plugins.MARK_ID;
import static com.holub.life.host.fixture.FakePhase2Plugins.MARK_PROPERTY;
import static com.holub.life.host.fixture.FakePhase2Plugins.NEIGHBOR_COUNT_PROPERTY;
import static com.holub.life.host.fixture.FakePhase2Plugins.OBSERVE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.StepContext;
import com.holub.life.host.fixture.FakePhase2Plugins;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class HostGameEngineTest {
    private static final String CORE = "test:default-conway";
    private static final CellState EMPTY = new CellState(CoreCells.EMPTY, Map.of());
    private static final CellState ALIVE = new CellState(CoreCells.ALIVE, Map.of());

    @Test
    void conwayBlinkerReturnsToItsOriginalOrientationAfterTwoTicks() {
        HostGameEngine engine = engine();
        SimulationPlan plan = plan(CORE);
        BoardState input = board(5, 5, new GridPos(1, 2), new GridPos(2, 2), new GridPos(3, 2));
        BoardState expected = board(5, 5, new GridPos(2, 1), new GridPos(2, 2), new GridPos(2, 3));

        BoardState next = engine.calcNext(input, plan);
        assertEquals(input.generation() + 1, next.generation());
        assertEquals(input.seed(), next.seed());
        assertEquals(expected.cells(), next.cells());
        assertEquals(input.cells(), engine.calcNext(next, plan).cells());
    }

    @Test
    void conwayBlockRemainsStableAndGliderMovesDiagonallyAfterFourTicks() {
        HostGameEngine engine = engine();
        SimulationPlan plan = plan(CORE);
        BoardState block = board(6, 6, new GridPos(1, 1), new GridPos(2, 1),
            new GridPos(1, 2), new GridPos(2, 2));
        assertEquals(block.cells(), engine.calcNext(block, plan).cells());

        BoardState glider = board(6, 6, new GridPos(1, 0), new GridPos(2, 1),
            new GridPos(0, 2), new GridPos(1, 2), new GridPos(2, 2));
        BoardState expected = board(6, 6, new GridPos(2, 1), new GridPos(3, 2),
            new GridPos(1, 3), new GridPos(2, 3), new GridPos(3, 3));
        for (int step = 0; step < 4; step++) {
            glider = engine.calcNext(glider, plan);
        }
        assertEquals(expected.cells(), glider.cells());
    }

    @Test
    void planOrderDeterminesWhichWholeBoardTheNextPluginSees() {
        // Registry order is deliberately opposite to this plan's order.
        HostGameEngine engine = engine(FakePhase2Plugins.observe(), FakePhase2Plugins.mark());
        BoardState input = board(2, 2);
        BoardState markedFirst = engine.calcNext(input, plan(MARK_ID, OBSERVE_ID));
        BoardState observedFirst = engine.calcNext(input, plan(OBSERVE_ID, MARK_ID));

        assertEquals(List.of("3", "3", "3", "3"), counts(markedFirst));
        assertEquals(List.of("0", "0", "0", "0"), counts(observedFirst));
        assertEquals(input.generation() + 1, markedFirst.generation());
        assertEquals(board(2, 2), input);
        for (CellState cell : markedFirst.cells()) {
            assertSame(CoreCells.EMPTY, cell.type());
        }
    }

    @Test
    void appliesEachRegistrationOfOnePluginInOrderAndSkipsDisabledPlugins() {
        String chainId = "test:chain";
        LifePlugin chain = plugin(chainId, registrar -> {
            FakePhase2Plugins.mark().register(registrar);
            FakePhase2Plugins.observe().register(registrar);
        });
        HostGameEngine engine = engine(chain, FakePhase2Plugins.observe());
        BoardState input = board(2, 2);
        assertEquals(List.of("3", "3", "3", "3"),
            counts(engine.calcNext(input, plan(chainId))));

        var disabledChain = new SimulationPlan(BoundedAccessor.ID,
            List.of(new PluginSelection(chainId, false), new PluginSelection(OBSERVE_ID, true)), Map.of());
        BoardState result = engine.calcNext(input, disabledChain);
        assertEquals(List.of("0", "0", "0", "0"), counts(result));
        for (CellState cell : result.cells()) {
            assertFalse(cell.properties().containsKey(MARK_PROPERTY));
        }
    }

    @Test
    void enabledReplacementSuppressesOnlyTheDesignatedDefaultLifecycle() {
        String replacementId = "test:replace-default";
        LifePlugin replacement = plugin(replacementId, registrar -> {
            registrar.replaceDefault(ApiPoint.LIFECYCLE);
            FakePhase2Plugins.mark().register(registrar);
        });
        HostGameEngine engine = engine(replacement, FakePhase2Plugins.observe());
        var center = new GridPos(0, 0);
        BoardState source = board(2, 2, center);
        var enabled = plan(CORE, replacementId, OBSERVE_ID);

        // The default Conway rule would kill this isolated cell. The replacement marks it,
        // while the other enabled stage still observes the full marked board.
        BoardState replaced = engine.calcNext(source, enabled);
        assertSame(CoreCells.ALIVE, replaced.cellAt(center).type());
        assertEquals(List.of("3", "3", "3", "3"), counts(replaced));

        var disabled = new SimulationPlan(BoundedAccessor.ID,
            List.of(new PluginSelection(CORE, true), new PluginSelection(replacementId, false),
                new PluginSelection(OBSERVE_ID, true)), Map.of());
        BoardState restored = engine.calcNext(source, disabled);
        assertSame(CoreCells.EMPTY, restored.cellAt(center).type());
        assertEquals(List.of("0", "0", "0", "0"), counts(restored));
    }

    @Test
    void selectedAccessorUsesTheUpdatedSnapshotAtEachStage() {
        String accessorId = "test:wrapping";
        LifePlugin wrapping = plugin("test:topology", registrar ->
            registrar.registerAccessor(new AccessorFactory<GridPos>() {
                @Override
                public String id() {
                    return accessorId;
                }

                @Override
                public Class<GridPos> coordinateType() {
                    return GridPos.class;
                }

                @Override
                public Accessor<GridPos> bind(BoardState source) {
                    return pos -> {
                        var canonical = new GridPos(Math.floorMod(pos.x(), source.size().width()),
                            Math.floorMod(pos.y(), source.size().height()));
                        return Optional.of(new CellSample(canonical, source.cellAt(canonical)));
                    };
                }
            }));
        HostGameEngine engine = engine(wrapping, FakePhase2Plugins.mark(), FakePhase2Plugins.observe());
        var plan = new SimulationPlan(accessorId, List.of(new PluginSelection("test:topology", true),
            new PluginSelection(MARK_ID, true), new PluginSelection(OBSERVE_ID, true)), Map.of());

        assertEquals(Collections.nCopies(6, "5"), counts(engine.calcNext(board(3, 2), plan)));
    }

    @Test
    void rngReplaysAfterASeparateTickAndAfterAPartialFailure() {
        String checkpointId = "test:post-mark-checkpoint";
        var failure = new IllegalStateException("Injected failure after marking");
        HostGameEngine engine = engine(FakePhase2Plugins.mark(), checkpoint(checkpointId, failure));
        SimulationPlan plan = plan(MARK_ID, checkpointId);
        BoardState input = board(2, 2, new GridPos(1, 1));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> engine.calcNext(input, plan)));

        BoardState retried = engine.calcNext(input, plan);
        HostGameEngine fresh = engine(FakePhase2Plugins.mark(), checkpoint(checkpointId, null));
        BoardState expected = fresh.calcNext(input, plan);
        assertEquals(expected, retried);

        engine.calcNext(retried, plan);
        assertEquals(expected, engine.calcNext(input, plan));
    }

    @Test
    void rejectsUnknownPlanIdsAndUnsupportedPluginOptions() {
        HostGameEngine engine = engine();
        assertThrows(IllegalArgumentException.class,
            () -> engine.validatePlan(new SimulationPlan("missing:accessor", List.of(), Map.of())));
        assertThrows(IllegalArgumentException.class,
            () -> engine.calcNext(board(1, 1), plan("missing:plugin")));
        var planWithOptions = new SimulationPlan(BoundedAccessor.ID, List.of(new PluginSelection(CORE, true)),
            Map.of(CORE, Map.of("unsupported", "yes")));
        assertThrows(IllegalArgumentException.class, () -> engine.validatePlan(planWithOptions));
    }

    @Test
    void rejectsUnregisteredTypeIdentityInInputAndRuleOutput() {
        CellType impostor = new CellType(CoreCells.EMPTY.id()) {
            @Override
            public boolean isAlive(CellState state) {
                return false;
            }
        };
        CellState invalid = new CellState(impostor, Map.of());
        String invalidRuleId = "test:invalid";
        LifePlugin invalidRule = plugin(invalidRuleId,
            registrar -> registrar.registerLifecycle(new LifecycleManager(0) {
                @Override
                public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                    return invalid;
                }
            }));
        HostGameEngine engine = engine(invalidRule);
        BoardState badInput = new BoardState(new BoardSize(1, 1), 0, 0, List.of(invalid));
        assertThrows(IllegalArgumentException.class, () -> engine.calcNext(badInput, plan()));
        assertThrows(IllegalArgumentException.class, () -> engine.calcNext(board(1, 1), plan(invalidRuleId)));
        BoardState input = board(1, 1);
        BoardState result = engine.calcNext(input, plan());
        assertEquals(input.cells(), result.cells());
        assertEquals(input.generation() + 1, result.generation());
    }

    private static LifePlugin checkpoint(String id, IllegalStateException firstFailure) {
        var pending = new AtomicReference<>(firstFailure);
        return plugin(id, registrar -> registrar.registerLifecycle(new LifecycleManager(0) {
            @Override
            public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                CellState marked = neighbors.center().state();
                // A failed attempt must already have received the result of the mark stage.
                Long.parseLong(marked.properties().get(MARK_PROPERTY));
                IllegalStateException failure = pending.getAndSet(null);
                if (failure != null) {
                    throw failure;
                }
                return marked;
            }
        }));
    }

    private static List<String> counts(BoardState board) {
        return board.cells().stream().map(cell -> cell.properties().get(NEIGHBOR_COUNT_PROPERTY)).toList();
    }

    private static HostGameEngine engine(LifePlugin... plugins) {
        var all = new ArrayList<LifePlugin>();
        all.add(plugin(CORE, registrar -> {
            registrar.registerAccessor(new BoundedAccessor());
            registrar.registerLifecycle(new CoreConwayRule());
        }));
        all.addAll(List.of(plugins));
        return new HostGameEngine(new HostRegistry(all), CORE);
    }

    private static SimulationPlan plan(String... ids) {
        var selections = Arrays.stream(ids).map(id -> new PluginSelection(id, true)).toList();
        return new SimulationPlan(BoundedAccessor.ID, selections, Map.of());
    }

    private static LifePlugin plugin(String id, Consumer<PluginRegistrar> register) {
        return new LifePlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void register(PluginRegistrar registrar) {
                register.accept(registrar);
            }
        };
    }

    private static BoardState board(int width, int height, GridPos... alive) {
        var cells = new ArrayList<>(Collections.nCopies(width * height, EMPTY));
        for (GridPos pos : alive) {
            cells.set(pos.y() * width + pos.x(), ALIVE);
        }
        return new BoardState(new BoardSize(width, height), 8, 25, cells);
    }
}
