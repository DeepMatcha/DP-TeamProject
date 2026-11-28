package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.HostError;
import com.holub.life.api.HostErrorListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.Pattern;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The host's single entry point for commands (Facade over engine, model, playback and plan policy).
 * Every command becomes one task on a single operation thread, so commands, ticks and plan
 * changes are serialized in the order the host accepted them and never interleave mid-tick.
 */
public final class HostController implements ControllerPort, AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HostController.class.getName());

    private final HostRegistry registry;
    private final HostGameEngine engine;
    private final HostBoardModel model;
    private final ScheduledThreadPoolExecutor operations;
    private final PlaybackTimer playback;
    private final PluginSwitch pluginSwitch;
    private final CopyOnWriteArrayList<HostErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<SimulationPlan>> planListeners = new CopyOnWriteArrayList<>();

    // Only the operations thread changes the plan. Views may read the immutable value.
    private volatile SimulationPlan plan;

    public HostController(HostRegistry registry, BoardState initial, SimulationPlan plan) {
        this(registry, initial, plan, null);
    }

    HostController(HostRegistry registry, BoardState initial, SimulationPlan plan, String defaultLifecyclePluginId) {
        this.registry = Objects.requireNonNull(registry);
        engine = new HostGameEngine(registry, defaultLifecyclePluginId);
        engine.validateBoard(initial);
        engine.validatePlan(plan);
        model = new HostBoardModel(initial);
        pluginSwitch = new PluginSwitch(registry, plan.accessorId());
        this.plan = plan;
        operations = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = new Thread(task, "life-host-operations");
            thread.setDaemon(true);
            return thread;
        });
        operations.setRemoveOnCancelPolicy(true);
        playback = new PlaybackTimer(operations, this::automaticTick);
    }

    public ModelPort model() {
        return model;
    }

    @Override
    public CompletionStage<BoardState> step() {
        return submit(this::tick);
    }

    private BoardState tick() {
        BoardState next = engine.calcNext(model.snapshot(), plan);
        model.commit(next);
        return next;
    }

    @Override
    public CompletionStage<BoardState> apply(BoardPatch patch) {
        Objects.requireNonNull(patch);
        return submit(() -> commitPatch(patch));
    }

    /** Resolve the tool and its click against the state at the operation boundary. */
    public CompletionStage<BoardState> applyTool(String toolId, ToolInput input) {
        Objects.requireNonNull(toolId);
        Objects.requireNonNull(input);
        return submit(() -> {
            boolean enabled = registry.activeRegistrations(plan, ApiPoint.INPUT_TOOL).stream()
                    .anyMatch(registration -> registration.id().equals(toolId));
            if (!enabled) {
                throw new IllegalArgumentException("Input tool is not enabled: " + toolId);
            }
            var tool = registry.inputTool(toolId).orElseThrow();
            BoardState current = model.snapshot();
            var accessor = registry.accessor(plan.accessorId()).orElseThrow().bind(current);
            return commitPatch(tool.apply(input, current, accessor));
        });
    }

    /** Replace the contents of the current board with a legacy pattern, after validation. */
    public CompletionStage<BoardState> replacePattern(Pattern pattern, CellState empty) {
        Objects.requireNonNull(pattern);
        Objects.requireNonNull(empty);
        return submit(() -> {
            BoardState current = model.snapshot();
            if (pattern.size().width() > current.size().width()
                    || pattern.size().height() > current.size().height()) {
                throw new IllegalArgumentException("Pattern does not fit the current board");
            }
            for (CellEdit edit : pattern.cells()) {
                if (!BoundedAccessor.contains(pattern.size(), edit.position())) {
                    throw new IllegalArgumentException("Cell is outside the pattern: " + edit.position());
                }
            }
            BoardState next = patched(BoardEdits.replacement(current, pattern, empty));
            playback.stop();
            model.commit(next);
            return next;
        });
    }

    public CompletionStage<BoardState> clear(CellState empty) {
        Objects.requireNonNull(empty);
        return submit(() -> commitPatch(BoardEdits.replacement(model.snapshot(), null, empty)));
    }

    private BoardState commitPatch(BoardPatch patch) {
        BoardState current = model.snapshot();
        BoardState next = patched(patch);
        if (next != current) {
            model.commit(next);
        }
        return next;
    }

    private BoardState patched(BoardPatch patch) {
        return BoardEdits.applied(model.snapshot(), patch, engine::validateCell);
    }

    @Override
    public CompletionStage<Void> setRunning(boolean running) {
        return submit(() -> {
            if (running) {
                playback.start();
            } else {
                playback.stop();
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> setTickIntervalMillis(int milliseconds) {
        return submit(() -> {
            playback.setInterval(milliseconds);
            return null;
        });
    }

    /** Apply a Swing speed preset and start as one host operation. */
    public CompletionStage<Void> playAtInterval(int milliseconds) {
        return submit(() -> {
            playback.setInterval(milliseconds);
            playback.start();
            return null;
        });
    }

    @Override
    public CompletionStage<PlaybackState> playbackState() {
        return submit(playback::state);
    }

    @Override
    public CompletionStage<GameSnapshot> session() {
        return submit(() -> new GameSnapshot(model.snapshot(), plan, registry.pluginVersions()));
    }

    @Override
    public CompletionStage<BoardState> restore(GameSnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        return submit(() -> {
            engine.validatePlan(snapshot.plan());
            engine.validateBoard(snapshot.board());
            SnapshotVersions.check(snapshot, registry);
            playback.stop();
            plan = snapshot.plan();
            model.commit(snapshot.board());
            publishPlan();
            return snapshot.board();
        });
    }

    @Override
    public CompletionStage<Void> configure(SimulationPlan next) {
        Objects.requireNonNull(next);
        return submit(() -> {
            configurePlan(next);
            return null;
        });
    }

    /** Switches one provider at the operation boundary without replacing the board; see {@link PluginSwitch}. */
    public CompletionStage<Void> setPluginEnabled(String pluginId, boolean enabled) {
        Objects.requireNonNull(pluginId, "pluginId");
        return submit(() -> {
            pluginSwitch.switched(plan, pluginId, enabled).ifPresent(this::configurePlan);
            return null;
        });
    }

    private void configurePlan(SimulationPlan next) {
        engine.validatePlan(next);
        plan = next;
        publishPlan();
    }

    /** For View startup. Use session() when the plan and board must be captured together. */
    SimulationPlan currentPlan() {
        return plan;
    }

    /** Receives the committed plan on the host operation thread after configure or restore. */
    public void addPlanListener(Consumer<SimulationPlan> listener) {
        planListeners.add(Objects.requireNonNull(listener));
    }

    public void removePlanListener(Consumer<SimulationPlan> listener) {
        planListeners.remove(listener);
    }

    private void publishPlan() {
        for (Consumer<SimulationPlan> listener : planListeners) {
            ExtensionCalls.failureOf(() -> listener.accept(plan)).ifPresent(error ->
                    LOG.log(System.Logger.Level.WARNING, "Plan listener failed", error));
        }
    }

    @Override
    public HostRegistry registry() {
        return registry;
    }

    @Override
    public void addErrorListener(HostErrorListener listener) {
        errorListeners.add(Objects.requireNonNull(listener));
    }

    @Override
    public void removeErrorListener(HostErrorListener listener) {
        errorListeners.remove(listener);
    }

    private void automaticTick() {
        ExtensionCalls.failureOf(this::tick).ifPresent(error -> {
            playback.stop();
            var failure = new HostError("automatic-tick", error.toString(), error);
            for (HostErrorListener listener : errorListeners) {
                ExtensionCalls.failureOf(() -> listener.onError(failure)).ifPresent(listenerError ->
                        LOG.log(System.Logger.Level.WARNING, "Host error listener failed", listenerError));
            }
        });
    }

    private <T> CompletionStage<T> submit(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        try {
            operations.execute(() -> {
                try {
                    result.complete(action.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result.minimalCompletionStage();
    }

    @Override
    public void close() {
        operations.shutdown();
    }
}
