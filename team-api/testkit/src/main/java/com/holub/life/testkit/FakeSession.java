package com.holub.life.testkit;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GameSnapshot;
import com.holub.life.api.GridPos;
import com.holub.life.api.HostErrorListener;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.RegistryView;
import com.holub.life.api.SimulationPlan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous Model + Controller over a {@link FakeHost}, for View tests and demos.
 * Every request completes before it returns; there is no timer, so setRunning only records the flag.
 */
public final class FakeSession implements ControllerPort, ModelPort {
    private final FakeHost host;
    private final List<ModelListener> listeners = new CopyOnWriteArrayList<>();
    private final List<HostErrorListener> errorListeners = new CopyOnWriteArrayList<>();
    private BoardState board;
    private SimulationPlan plan;
    private boolean running;
    private int interval = 500;

    public FakeSession(FakeHost host, BoardState initial) { this(host, initial, FakeHost.BOUNDED_ACCESSOR); }

    public FakeSession(FakeHost host, BoardState initial, String accessorId) {
        this.host = host;
        this.board = initial;
        this.plan = new SimulationPlan(accessorId,
                host.plugins().stream().map(info -> new PluginSelection(info.id(), true)).toList(), Map.of());
    }

    public int listenerCount() { return listeners.size(); }
    public int errorListenerCount() { return errorListeners.size(); }

    @Override public synchronized BoardState snapshot() { return board; }
    @Override public void addListener(ModelListener listener) { listeners.add(listener); }
    @Override public void removeListener(ModelListener listener) { listeners.remove(listener); }

    @Override public synchronized CompletionStage<BoardState> step() {
        return commit(host.step(board, plan.accessorId()));
    }

    @Override public synchronized CompletionStage<BoardState> apply(BoardPatch patch) {
        Map<GridPos, CellState> lastWrites = new LinkedHashMap<>();
        for (CellEdit edit : patch.edits()) lastWrites.put(edit.position(), edit.value());
        List<CellState> cells = new ArrayList<>(board.cells());
        for (var write : lastWrites.entrySet()) {
            GridPos pos = write.getKey();
            if (pos.x() < 0 || pos.y() < 0 || pos.x() >= board.size().width() || pos.y() >= board.size().height())
                return CompletableFuture.failedFuture(new IllegalArgumentException("Outside board: " + pos));
            if (host.idOf(write.getValue().type()).isEmpty())
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Unregistered type: " + write.getValue().type().id()));
            cells.set(pos.y() * board.size().width() + pos.x(), write.getValue());
        }
        return commit(new BoardState(board.size(), board.generation(), board.seed(), cells));
    }

    private CompletionStage<BoardState> commit(BoardState next) {
        board = next;
        for (ModelListener listener : listeners) {
            try { listener.onChanged(next); } catch (RuntimeException ignored) { /* a View error must not undo a commit */ }
        }
        return CompletableFuture.completedFuture(next);
    }

    @Override public synchronized CompletionStage<Void> setRunning(boolean running) {
        this.running = running;
        return CompletableFuture.completedFuture(null);
    }

    @Override public synchronized CompletionStage<Void> setTickIntervalMillis(int milliseconds) {
        if (milliseconds <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("Not positive"));
        interval = milliseconds;
        return CompletableFuture.completedFuture(null);
    }

    @Override public synchronized CompletionStage<PlaybackState> playbackState() {
        return CompletableFuture.completedFuture(new PlaybackState(running, interval));
    }

    @Override public synchronized CompletionStage<GameSnapshot> session() {
        return CompletableFuture.completedFuture(new GameSnapshot(board, plan, Map.of()));
    }

    @Override public synchronized CompletionStage<BoardState> restore(GameSnapshot snapshot) {
        plan = snapshot.plan();
        running = false;
        return commit(snapshot.board());
    }

    @Override public synchronized CompletionStage<Void> configure(SimulationPlan plan) {
        if (host.accessor(plan.accessorId()).isEmpty())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown accessor"));
        this.plan = plan;
        return CompletableFuture.completedFuture(null);
    }

    @Override public RegistryView registry() { return host; }
    @Override public void addErrorListener(HostErrorListener listener) { errorListeners.add(listener); }
    @Override public void removeErrorListener(HostErrorListener listener) { errorListeners.remove(listener); }
}
