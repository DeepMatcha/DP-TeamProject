package com.holub.life.api;
/**
 * Stateless computation: does not own current board, timer or history.
 * Same source (including seed/generation), plan and implementation must yield
 * the same result. An earlier call must not advance hidden randomness for this call.
 */
public interface GameEngine {
    BoardState calcNext(BoardState source, SimulationPlan plan);
}
