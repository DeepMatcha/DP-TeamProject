import com.holub.life.api.*;
import java.util.*;
import java.util.concurrent.*;

/** Compile-only Phase 1 stub. No dependency on Phase 2 classes; not a real host. */
public class HostCompileProbe implements ControllerPort {
    private static <T> CompletionStage<T> unimplemented() {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("Compile-only stub"));
    }
    public CompletionStage<BoardState> step() { return unimplemented(); }
    public CompletionStage<BoardState> apply(BoardPatch patch) { return unimplemented(); }
    public CompletionStage<Void> setRunning(boolean running) { return unimplemented(); }
    public CompletionStage<Void> setTickIntervalMillis(int milliseconds) { return unimplemented(); }
    public CompletionStage<PlaybackState> playbackState() { return unimplemented(); }
    public CompletionStage<GameSnapshot> session() { return unimplemented(); }
    public CompletionStage<BoardState> restore(GameSnapshot snapshot) { return unimplemented(); }
    public CompletionStage<Void> configure(SimulationPlan plan) { return unimplemented(); }
    public RegistryView registry() { throw new UnsupportedOperationException("Compile-only stub"); }
    public void addErrorListener(HostErrorListener listener) {
        throw new UnsupportedOperationException("Compile-only stub");
    }
    public void removeErrorListener(HostErrorListener listener) {
        throw new UnsupportedOperationException("Compile-only stub");
    }
}
