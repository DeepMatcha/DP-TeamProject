package com.holub.life.api;
/** Does not own current BoardState, timer, history, or UI. */
public interface GameEngine {
    BoardState calcNext(BoardState source, SimulationPlan plan);
}
