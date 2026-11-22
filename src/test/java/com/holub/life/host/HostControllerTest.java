package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.GridPos;
import com.holub.life.api.HostError;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.Pattern;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.StepContext;
import com.holub.life.host.fixture.FakePhase2Plugins;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostControllerTest {
    private static final GridPos ORIGIN = new GridPos(0, 0);
    private static final CellState DEAD = new CellState(CoreCells.EMPTY, Map.of());
    private static final CellState LIVE = new CellState(CoreCells.ALIVE, Map.of());

    @Test
    void patchesValidateOnlyFinalValuesAndRollBackAsAUnit() throws Exception {
        try (var controller = controller()) {
            var notices = new AtomicInteger();
            controller.model().addListener(board -> notices.incrementAndGet());
            var impostor = new CellType(CoreCells.ALIVE.id()) {
                @Override
                public boolean isAlive(CellState state) {
                    return true;
                }
            };
            var unregistered = new CellState(impostor, Map.of());
            var shadowed = new BoardPatch(List.of(new CellEdit(ORIGIN, unregistered), new CellEdit(ORIGIN, LIVE)));
            var board = controller.apply(shadowed).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(LIVE, board.cellAt(ORIGIN));
            assertEquals(8, board.generation());
            assertEquals(42, board.seed());
            assertEquals(1, notices.get());

            var bad = new BoardPatch(List.of(new CellEdit(ORIGIN, DEAD),
                    new CellEdit(new GridPos(2, 1), LIVE)));
            assertThrows(ExecutionException.class, () -> controller.apply(bad).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(board, controller.model().snapshot());
            assertEquals(1, notices.get());
        }
    }

    @Test
    void acceptedEditAndEveryStepAreSerializedAndPublishBeforeCompletion() throws Exception {
        try (var controller = controller()) {
            var publishing = new LinkedBlockingQueue<BoardState>();
            var delivered = new CopyOnWriteArrayList<BoardState>();
            var allowDelivery = new Semaphore(0);
            controller.model().addListener(board -> { throw new NoClassDefFoundError("View unavailable"); });
            controller.model().addListener(board -> {
                publishing.add(board);
                try {
                    if (allowDelivery.tryAcquire(10, TimeUnit.SECONDS)) {
                        delivered.add(board);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            try {
                var pending = List.of(
                        controller.apply(new BoardPatch(List.of(new CellEdit(ORIGIN, LIVE))))
                                .toCompletableFuture(),
                        controller.step().toCompletableFuture(),
                        controller.step().toCompletableFuture());
                var checked = pending.stream().map(request -> request.thenApply(committed -> {
                    assertTrue(delivered.stream().anyMatch(board -> board == committed),
                            "Completion must follow delivery of this exact board");
                    return committed;
                })).toList();

                for (int index = 0; index < pending.size(); index++) {
                    BoardState notification = publishing.poll(5, TimeUnit.SECONDS);
                    assertNotNull(notification, "Expected one notification per accepted request");
                    assertEquals(8 + index, notification.generation());
                    assertEquals(LIVE, notification.cellAt(ORIGIN));
                    assertSame(notification, controller.model().snapshot());
                    assertFalse(pending.get(index).isDone(),
                            "Request completed while its publication was gated");

                    allowDelivery.release();
                    assertSame(notification, checked.get(index).get(5, TimeUnit.SECONDS));
                    assertEquals(index + 1, delivered.size());
                    assertSame(notification, delivered.get(index));
                }
                assertTrue(publishing.isEmpty());
            } finally {
                allowDelivery.release(3);
            }
        }
    }

    @Test
    void serviceConfigurationErrorInModelListenerDoesNotFailCommittedEdit() throws Exception {
        try (var controller = controller()) {
            var delivered = new AtomicReference<BoardState>();
            controller.model().addListener(board -> {
                throw new ServiceConfigurationError("Fixture view provider unavailable");
            });
            controller.model().addListener(delivered::set);

            BoardState edited = controller.apply(new BoardPatch(List.of(new CellEdit(ORIGIN, LIVE))))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(LIVE, edited.cellAt(ORIGIN));
            assertSame(edited, controller.model().snapshot());
            assertSame(edited, delivered.get());
        }
    }

    @Test
    void restoresAndReplaysSnapshotAndRejectsIncompatibleVersions() throws Exception {
        String mark = FakePhase2Plugins.MARK_ID;
        var registry = new HostRegistry(List.of(boardPlugin(), FakePhase2Plugins.mark()),
                Map.of(mark, Map.of("version", "fixture-v1")));
        try (var controller = new HostController(registry, initial(), plan(mark))) {
            var checkpoint = controller.session().toCompletableFuture().get(5, TimeUnit.SECONDS);
            var next = controller.step().toCompletableFuture().get(5, TimeUnit.SECONDS);
            var invalid = new GameSnapshot(checkpoint.board(), checkpoint.plan(), Map.of(mark, "other-version"));
            assertThrows(ExecutionException.class,
                    () -> controller.restore(invalid).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(next, controller.model().snapshot());

            controller.setTickIntervalMillis(1000).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.setRunning(true).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.restore(checkpoint).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(new PlaybackState(false, 1000),
                    controller.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(next, controller.step().toCompletableFuture().get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void failedAutomaticTickStopsPlaybackAndReportsThroughErrorListener() throws Exception {
        assertFailedManualAndAutomaticTick(new NoClassDefFoundError("Fixture rule unavailable"));
    }

    @Test
    void serviceConfigurationErrorStopsAutomaticTickAndReportsItsCause() throws Exception {
        assertFailedManualAndAutomaticTick(new ServiceConfigurationError("Fixture rule provider unavailable"));
    }

    private static void assertFailedManualAndAutomaticTick(Error ruleError) throws Exception {
        var failing = failingPlugin(ruleError);
        BoardState board = initial();
        var registry = new HostRegistry(List.of(boardPlugin(), failing));
        try (var controller = new HostController(registry, board, plan(failing.id()))) {
            var manual = assertThrows(ExecutionException.class,
                    () -> controller.step().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(ruleError, manual.getCause());
            assertSame(board, controller.model().snapshot());

            var reported = new CountDownLatch(1);
            var failure = new AtomicReference<HostError>();
            controller.addErrorListener(error -> {
                failure.set(error);
                reported.countDown();
            });
            controller.setTickIntervalMillis(10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.setRunning(true).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertTrue(reported.await(5, TimeUnit.SECONDS), "Expected automatic tick failure notification");
            assertEquals("automatic-tick", failure.get().operation());
            assertSame(ruleError, failure.get().cause());
            assertEquals(new PlaybackState(false, 10),
                    controller.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(board, controller.model().snapshot());
        }
    }

    @Test
    void serviceConfigurationErrorInErrorListenerDoesNotSuppressTheNextListener() throws Exception {
        var ruleError = new NoClassDefFoundError("Fixture rule unavailable");
        var failing = failingPlugin(ruleError);
        var board = initial();
        var registry = new HostRegistry(List.of(boardPlugin(), failing));
        try (var controller = new HostController(registry, board, plan(failing.id()))) {
            var reported = new CountDownLatch(1);
            var failure = new AtomicReference<HostError>();
            controller.addErrorListener(error -> {
                throw new ServiceConfigurationError("Fixture error listener provider unavailable");
            });
            controller.addErrorListener(error -> {
                failure.set(error);
                reported.countDown();
            });

            controller.setTickIntervalMillis(10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.setRunning(true).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertTrue(reported.await(5, TimeUnit.SECONDS), "Expected the following listener to be notified");
            assertEquals("automatic-tick", failure.get().operation());
            assertSame(ruleError, failure.get().cause());
            assertEquals(new PlaybackState(false, 10),
                    controller.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(board, controller.model().snapshot());
        }
    }

    private static LifePlugin failingPlugin(Error ruleError) {
        return new LifePlugin() {
            @Override
            public String id() {
                return "test:failure";
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registrar.registerLifecycle(new LifecycleManager(0) {
                    @Override
                    public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                        throw ruleError;
                    }
                });
            }
        };
    }

    @Test
    void pauseCompletesAfterAnAutomaticTickAndPreventsFurtherTicks() throws Exception {
        try (var controller = controller()) {
            var first = new CountDownLatch(1);
            controller.model().addListener(board -> first.countDown());
            controller.setTickIntervalMillis(10).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.setRunning(true).toCompletableFuture().get(5, TimeUnit.SECONDS);
            controller.setRunning(true).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertTrue(first.await(5, TimeUnit.SECONDS));
            controller.setRunning(false).toCompletableFuture().get(5, TimeUnit.SECONDS);
            BoardState paused = controller.model().snapshot();
            var afterPause = new CountDownLatch(1);
            controller.model().addListener(board -> afterPause.countDown());
            assertFalse(afterPause.await(100, TimeUnit.MILLISECONDS));
            assertSame(paused, controller.model().snapshot());
        }
    }

    @Test
    void patternReplacementDoesNotClearBoardOnInvalidInput() throws Exception {
        try (var controller = controller()) {
            var old = controller.apply(new BoardPatch(List.of(new CellEdit(ORIGIN, LIVE))))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            var size = new BoardSize(3, 2);
            var outside = new Pattern(size, List.of(new CellEdit(new GridPos(2, 0), LIVE)));
            assertThrows(ExecutionException.class,
                    () -> controller.replacePattern(outside, DEAD).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertSame(old, controller.model().snapshot());

            var target = new GridPos(1, 1);
            var pattern = new Pattern(new BoardSize(2, 2), List.of(new CellEdit(target, LIVE)));
            var replaced = controller.replacePattern(pattern, DEAD)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertEquals(DEAD, replaced.cellAt(ORIGIN));
            assertEquals(LIVE, replaced.cellAt(target));
            assertEquals(8, replaced.generation());
        }
    }

    @Test
    void configureChangesOnlyThePlanAndRejectsUnsupportedOptions() throws Exception {
        var registry = new HostRegistry(List.of(boardPlugin(), FakePhase2Plugins.mark()));
        try (var controller = new HostController(registry, initial(), plan())) {
            var notices = new AtomicInteger();
            controller.model().addListener(board -> notices.incrementAndGet());
            var old = controller.model().snapshot();
            var candidate = plan(FakePhase2Plugins.MARK_ID);
            controller.configure(candidate).toCompletableFuture().get(5, TimeUnit.SECONDS);
            var invalid = new SimulationPlan(candidate.accessorId(), candidate.plugins(),
                    Map.of(FakePhase2Plugins.MARK_ID, Map.of("unsupported", "value")));
            assertThrows(ExecutionException.class,
                    () -> controller.configure(invalid).toCompletableFuture().get(5, TimeUnit.SECONDS));
            var after = controller.session().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertSame(old, after.board());
            assertEquals(candidate, after.plan());
            assertEquals(0, notices.get());
        }
    }

    @Test
    void patchUsesRegisteredTypeIdentityEvenWhenTwoTypesCompareEqual() throws Exception {
        var before = new EqualType("test:first");
        var after = new EqualType("test:second");
        var types = new LifePlugin() {
            @Override
            public String id() {
                return "test:types";
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registrar.registerCellType(before);
                registrar.registerCellType(after);
            }
        };
        var registry = new HostRegistry(List.of(boardPlugin(), types));
        var start = new BoardState(new BoardSize(1, 1), 0, 0, List.of(new CellState(before, Map.of())));
        try (var controller = new HostController(registry, start, plan())) {
            var patch = new BoardPatch(List.of(new CellEdit(ORIGIN, new CellState(after, Map.of()))));
            var edited = controller.apply(patch).toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertSame(after, edited.cellAt(ORIGIN).type());
            assertSame(edited, controller.model().snapshot());
        }
    }

    private static final class EqualType extends CellType {
        EqualType(String id) {
            super(id);
        }

        @Override
        public boolean isAlive(CellState state) {
            return true;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualType;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static HostController controller() {
        return new HostController(new HostRegistry(List.of(boardPlugin())), initial(), plan());
    }

    private static BoardState initial() {
        return new BoardState(new BoardSize(2, 2), 8, 42, List.of(DEAD, DEAD, DEAD, DEAD));
    }

    private static SimulationPlan plan(String... ids) {
        var selected = new java.util.ArrayList<PluginSelection>();
        selected.add(new PluginSelection("test:board", true));
        for (String id : ids) {
            selected.add(new PluginSelection(id, true));
        }
        return new SimulationPlan(BoundedAccessor.ID, selected, Map.of());
    }

    private static LifePlugin boardPlugin() {
        return new LifePlugin() {
            @Override
            public String id() {
                return "test:board";
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registrar.registerCellType(CoreCells.EMPTY);
                registrar.registerCellType(CoreCells.ALIVE);
                registrar.registerAccessor(new BoundedAccessor());
            }
        };
    }
}
