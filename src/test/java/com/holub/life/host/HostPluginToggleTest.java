package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellSample;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.ModelPort;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import com.holub.life.api.ViewPlugin;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostPluginToggleTest {
    private static final BoardSize SIZE = new BoardSize(2, 2);
    private static final String MAPPING = "test:map";
    private static final String MAPPED_ACCESSOR = "test:wrap";
    private static final String UI = "test:ui";
    private static final String VIEW = "test:ui/panel";
    private static final CellType EXTENSION = new CellType("test:painted") {
        @Override
        public boolean isAlive(CellState state) { return false; }
    };

    @Test
    void catalogTogglesKeepBoardAndPlaybackAndSwitchTheAccessorUsedByTools() throws Exception {
        var mapping = plugin(MAPPING, registrar -> {
            registrar.registerAccessor(accessor(MAPPED_ACCESSOR));
            registrar.registerCellType(EXTENSION);
        });
        var ui = plugin(UI, registrar -> {
            registrar.registerView(view());
            registrar.replaceDefault(ApiPoint.VIEW);
        });
        try (var host = CoreGame.newSession(SIZE, 74, List.of(mapping, ui))) {
            var origin = new GridPos(0, 0);
            var state = new CellState(EXTENSION, Map.of("test:color", "blue"));
            await(host.apply(new BoardPatch(List.of(new CellEdit(origin, state)))));
            var initialBoard = await(host.step());
            await(host.setTickIntervalMillis(60_000));
            await(host.setRunning(true));
            var plans = new CopyOnWriteArrayList<SimulationPlan>();
            var boards = new CopyOnWriteArrayList<BoardState>();
            host.addPlanListener(plans::add);
            host.model().addListener(boards::add);

            var enableUi = host.setPluginEnabled(UI, true);
            var enableMapping = host.setPluginEnabled(MAPPING, true);
            await(enableUi);
            await(enableMapping);

            var active = host.currentPlan();
            assertEquals(MAPPED_ACCESSOR, active.accessorId());
            assertEquals(List.of("core:board", "core:conway", MAPPING, UI), pluginIds(active));
            assertEquals(List.of("core:board", "core:conway", UI), pluginIds(plans.getFirst()));
            assertEquals(List.of(VIEW), host.registry().activeRegistrations(active, ApiPoint.VIEW).stream()
                    .map(RegistrationInfo::id).toList());
            assertTrue(host.registry().replacesDefault(active, ApiPoint.VIEW));
            assertSame(initialBoard, host.model().snapshot());
            assertTrue(boards.isEmpty());
            assertEquals(new PlaybackState(true, 60_000), await(host.playbackState()));

            var wrappedClick = new ToolInput(new GridPos(-1, 1), "toggle", Map.of());
            var painted = await(host.applyTool(CoreGame.TOGGLE, wrappedClick));
            assertSame(CoreCells.ALIVE, painted.cellAt(new GridPos(1, 1)).type());

            await(host.setPluginEnabled(MAPPING, false));
            await(host.setPluginEnabled(UI, false));
            var disabled = host.currentPlan();
            assertEquals(BoundedAccessor.ID, disabled.accessorId());
            assertEquals(pluginIds(active), pluginIds(disabled));
            assertTrue(host.registry().activeRegistrations(disabled, ApiPoint.VIEW).isEmpty());
            assertFalse(host.registry().replacesDefault(disabled, ApiPoint.VIEW));
            assertTrue(host.registry().view(VIEW).isPresent(), "the catalog also lists disabled modules");
            assertSame(painted, host.model().snapshot());
            assertSame(EXTENSION, painted.cellAt(origin).type());
            assertEquals(state.properties(), painted.cellAt(origin).properties());
            assertEquals(1L, painted.generation());
            assertEquals(74L, painted.seed());
            assertEquals(List.of(painted), boards);
            assertThrows(ExecutionException.class, () -> await(host.applyTool(CoreGame.TOGGLE, wrappedClick)));
        }
    }

    @Test
    void explicitPlanOrderAndOriginalAccessorSurviveCheckboxToggles() throws Exception {
        var originalAccessor = plugin("test:default", registrar -> registrar.registerAccessor(accessor("default:map")));
        var alternative = plugin(MAPPING, registrar -> {
            registrar.registerAccessor(accessor(MAPPED_ACCESSOR));
            registrar.registerAccessor(accessor("test:later-in-same-provider"));
        });
        var other = plugin("test:other", registrar -> {});
        var registry = new HostRegistry(List.of(originalAccessor, alternative, other));
        var plan = new SimulationPlan("default:map",
                List.of(new PluginSelection(other.id(), true), new PluginSelection(originalAccessor.id(), true)),
                Map.of(other.id(), Map.of()));
        var initial = new BoardState(SIZE, 7, 58, Collections.nCopies(SIZE.area(), CoreGame.EMPTY));
        try (var host = new HostController(registry, initial, plan)) {
            await(host.setPluginEnabled(MAPPING, true));
            assertEquals(MAPPED_ACCESSOR, host.currentPlan().accessorId(), "first registration is selected");
            assertEquals(List.of(MAPPING, other.id(), originalAccessor.id()), pluginIds(host.currentPlan()));

            await(host.setPluginEnabled(MAPPING, false));
            assertEquals("default:map", host.currentPlan().accessorId());
            assertEquals(List.of(MAPPING, other.id(), originalAccessor.id()), pluginIds(host.currentPlan()));
            assertEquals(plan.pluginOptions(), host.currentPlan().pluginOptions());
            assertSame(initial, host.model().snapshot());
        }
    }

    @Test
    void disablingTheOnlyAvailableAccessorFailsBeforeChangingOrPublishingThePlan() throws Exception {
        var mapping = plugin(MAPPING, registrar -> registrar.registerAccessor(accessor(MAPPED_ACCESSOR)));
        var plan = new SimulationPlan(MAPPED_ACCESSOR, List.of(new PluginSelection(MAPPING, true)), Map.of());
        var initial = new BoardState(SIZE, 0, 29, Collections.nCopies(SIZE.area(), CoreGame.EMPTY));
        try (var host = new HostController(new HostRegistry(List.of(mapping)), initial, plan)) {
            var notices = new CopyOnWriteArrayList<SimulationPlan>();
            host.addPlanListener(notices::add);
            var failure = assertThrows(ExecutionException.class, () -> await(host.setPluginEnabled(MAPPING, false)));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertSame(plan, host.currentPlan());
            assertSame(initial, host.model().snapshot());
            assertTrue(notices.isEmpty());
        }
    }

    @Test
    void planNotificationsArePublishedAfterConfigureAndAfterTheBoardOfARestore() throws Exception {
        var module = plugin(UI, registrar -> registrar.registerView(view()));
        try (var host = CoreGame.newSession(SIZE, 74, List.of(module))) {
            var checkpoint = await(host.session());
            var edited = await(host.apply(new BoardPatch(List.of(new CellEdit(new GridPos(1, 1), CoreGame.ALIVE)))));
            var planChanges = new CopyOnWriteArrayList<PlanObservation>();
            Consumer<SimulationPlan> listener = plan ->
                    planChanges.add(new PlanObservation(plan, host.model().snapshot()));
            host.addPlanListener(listener);
            var boardCommits = new CopyOnWriteArrayList<BoardState>();
            host.model().addListener(boardCommits::add);

            var changed = new SimulationPlan(BoundedAccessor.ID, List.of(new PluginSelection(UI, true)), Map.of());
            await(host.configure(changed));
            assertEquals(List.of(new PlanObservation(changed, edited)), planChanges);
            assertTrue(boardCommits.isEmpty());

            var invalid = new SimulationPlan("missing:accessor", changed.plugins(), Map.of());
            assertThrows(ExecutionException.class, () -> await(host.configure(invalid)));
            assertSame(changed, host.currentPlan());
            assertEquals(1, planChanges.size());

            await(host.restore(checkpoint));
            assertEquals(List.of(new PlanObservation(changed, edited),
                    new PlanObservation(checkpoint.plan(), checkpoint.board())), planChanges);
            assertSame(checkpoint.board(), planChanges.getLast().board());
            assertEquals(List.of(checkpoint.board()), boardCommits);

            host.removePlanListener(listener);
            await(host.configure(changed));
            assertEquals(2, planChanges.size());
        }
    }

    private static List<String> pluginIds(SimulationPlan plan) {
        return plan.plugins().stream().map(PluginSelection::pluginId).toList();
    }

    private static LifePlugin plugin(String id, Consumer<PluginRegistrar> register) {
        return new LifePlugin() {
            @Override
            public String id() { return id; }
            @Override
            public void register(PluginRegistrar registrar) { register.accept(registrar); }
        };
    }

    private static ViewPlugin view() {
        return new ViewPlugin() {
            @Override
            public String id() { return VIEW; }
            @Override
            public void attach(ModelPort model, ControllerPort controller) {}
            @Override
            public void detach() {}
        };
    }

    private static AccessorFactory<GridPos> accessor(String id) {
        return new AccessorFactory<>() {
            @Override
            public String id() { return id; }
            @Override
            public Class<GridPos> coordinateType() { return GridPos.class; }
            @Override
            public Accessor<GridPos> bind(BoardState snapshot) {
                return requested -> {
                    var wrapped = new GridPos(Math.floorMod(requested.x(), snapshot.size().width()),
                            Math.floorMod(requested.y(), snapshot.size().height()));
                    return Optional.of(new CellSample(wrapped, snapshot.cellAt(wrapped)));
                };
            }
        };
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private record PlanObservation(SimulationPlan plan, BoardState board) {}
}
