package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.GridPos;
import com.holub.life.api.HostError;
import com.holub.life.api.HostErrorListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.Pattern;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.RegistryView;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Serializes session mutations and schedules ticks independently of the View. */
public final class HostController implements ControllerPort, AutoCloseable {
    private static final System.Logger LOG = System.getLogger(HostController.class.getName());

    private final HostRegistry registry;
    private final HostGameEngine engine;
    private final HostBoardModel model;
    private final ScheduledThreadPoolExecutor operations;
    private final CopyOnWriteArrayList<HostErrorListener> errorListeners = new CopyOnWriteArrayList<>();

    // These fields are owned by the operations thread.
    private SimulationPlan plan;
    private int intervalMillis = 150;
    private ScheduledFuture<?> timer;

    public HostController(HostRegistry registry, BoardState initial, SimulationPlan plan) {
        this.registry = Objects.requireNonNull(registry);
        engine = new HostGameEngine(registry);
        engine.validateBoard(initial);
        engine.validatePlan(plan);
        model = new HostBoardModel(initial);
        this.plan = plan;
        operations = new ScheduledThreadPoolExecutor(1, task -> {
            var thread = new Thread(task, "life-host-operations");
            thread.setDaemon(true);
            return thread;
        });
        operations.setRemoveOnCancelPolicy(true);
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
            boolean enabled = registry.registrations().stream().anyMatch(registration ->
                    registration.point() == ApiPoint.INPUT_TOOL && registration.id().equals(toolId)
                        && plan.plugins().contains(new PluginSelection(registration.pluginId(), true)));
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
            BoardState next = patchedBoard(replacementPatch(current, pattern, empty));
            stopTimer();
            model.commit(next);
            return next;
        });
    }

    public CompletionStage<BoardState> clear(CellState empty) {
        Objects.requireNonNull(empty);
        return submit(() -> commitPatch(replacementPatch(model.snapshot(), null, empty)));
    }

    private static BoardPatch replacementPatch(BoardState current, Pattern pattern, CellState empty) {
        var edits = new ArrayList<CellEdit>();
        for (int y = 0; y < current.size().height(); y++) {
            for (int x = 0; x < current.size().width(); x++) {
                edits.add(new CellEdit(new GridPos(x, y), empty));
            }
        }
        if (pattern != null) {
            edits.addAll(pattern.cells());
        }
        return new BoardPatch(edits);
    }

    private BoardState commitPatch(BoardPatch patch) {
        BoardState current = model.snapshot();
        BoardState next = patchedBoard(patch);
        if (!current.equals(next)) {
            model.commit(next);
        }
        return next;
    }

    private BoardState patchedBoard(BoardPatch patch) {
        var lastEdits = new LinkedHashMap<GridPos, CellState>();
        for (CellEdit edit : patch.edits()) {
            lastEdits.put(edit.position(), edit.value());
        }
        BoardState current = model.snapshot();
        var cells = new ArrayList<>(current.cells());
        lastEdits.forEach((position, state) -> {
            current.cellAt(position); // Check canonical coordinates without wrapping.
            engine.validateCell(state);
            cells.set(position.y() * current.size().width() + position.x(), state);
        });
        return new BoardState(current.size(), current.generation(), current.seed(), cells);
    }

    @Override
    public CompletionStage<Void> setRunning(boolean running) {
        return submit(() -> {
            if (running && timer == null) {
                startTimer();
            } else if (!running) {
                stopTimer();
            }
            return null;
        });
    }

    @Override
    public CompletionStage<Void> setTickIntervalMillis(int milliseconds) {
        return submit(() -> {
            changeTickInterval(milliseconds);
            return null;
        });
    }

    /** Apply a Swing speed preset and start as one host operation. */
    public CompletionStage<Void> playAtInterval(int milliseconds) {
        return submit(() -> {
            changeTickInterval(milliseconds);
            if (timer == null) {
                startTimer();
            }
            return null;
        });
    }

    private void changeTickInterval(int milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Tick interval must be positive");
        }
        if (intervalMillis != milliseconds) {
            intervalMillis = milliseconds;
            if (timer != null) {
                stopTimer();
                startTimer();
            }
        }
    }

    @Override
    public CompletionStage<PlaybackState> playbackState() {
        return submit(() -> new PlaybackState(timer != null, intervalMillis));
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
            checkVersions(snapshot);
            stopTimer();
            plan = snapshot.plan();
            model.commit(snapshot.board());
            return snapshot.board();
        });
    }

    private void checkVersions(GameSnapshot snapshot) {
        Map<String, String> installed = registry.pluginVersions();
        snapshot.pluginVersions().forEach((id, version) -> {
            if (!version.equals(installed.get(id))) {
                throw new IllegalArgumentException("Incompatible plugin version: " + id);
            }
        });
        for (PluginSelection selection : snapshot.plan().plugins()) {
            if (installed.containsKey(selection.pluginId())
                    && !snapshot.pluginVersions().containsKey(selection.pluginId())) {
                throw new IllegalArgumentException("Missing plugin version: " + selection.pluginId());
            }
        }
    }

    @Override
    public CompletionStage<Void> configure(SimulationPlan next) {
        Objects.requireNonNull(next);
        return submit(() -> {
            engine.validatePlan(next);
            plan = next;
            return null;
        });
    }

    @Override
    public RegistryView registry() {
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

    private void startTimer() {
        timer = operations.scheduleWithFixedDelay(this::automaticTick,
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel(false);
            timer = null;
        }
    }

    private void automaticTick() {
        try {
            tick();
        } catch (RuntimeException | AssertionError error) {
            stopTimer();
            var failure = new HostError("automatic-tick", error.toString(), error);
            for (HostErrorListener listener : errorListeners) {
                try {
                    listener.onError(failure);
                } catch (RuntimeException | AssertionError listenerError) {
                    LOG.log(System.Logger.Level.WARNING, "Host error listener failed", listenerError);
                }
            }
        }
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
