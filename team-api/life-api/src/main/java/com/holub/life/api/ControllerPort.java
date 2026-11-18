package com.holub.life.api;

import java.util.concurrent.CompletionStage;

/**
 * View input enters here. The host owns scheduling, validation and atomic commit.
 * CompletionStage reports completion/failure; it does not require worker threads.
 * Callers marshal callbacks to their UI thread when necessary, without blocking it.
 */
public interface ControllerPort {
    /** One entire tick; result completes after final Model commit/notification publication. */
    CompletionStage<BoardState> step();
    /** Direct edit: ordered assignments, last-write-wins, then atomic commit. */
    CompletionStage<BoardState> apply(BoardPatch patch);
    /** Repeated calls with the same desired state do not create extra timers. */
    CompletionStage<Void> setRunning(boolean running);
    /** Positive milliseconds; changing speed neither starts nor stops playback. */
    CompletionStage<Void> setTickIntervalMillis(int milliseconds);
    CompletionStage<PlaybackState> playbackState();
    /** Consistent full simulation snapshot: board, plan and plugin metadata versions. */
    CompletionStage<GameSnapshot> session();
    /** Validate first; atomically replace the session and leave playback stopped. */
    CompletionStage<BoardState> restore(GameSnapshot snapshot);
    /** Validate and replace the plan at an operation boundary, not halfway through a tick. */
    CompletionStage<Void> configure(SimulationPlan plan);
    RegistryView registry();
    /** Automatic host failures have no request caller, so they use this channel. */
    void addErrorListener(HostErrorListener listener);
    void removeErrorListener(HostErrorListener listener);
}
