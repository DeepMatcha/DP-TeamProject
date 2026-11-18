package com.holub.life.api;
import java.util.Objects;
import java.util.random.RandomGenerator;
/**
 * Context for one rule evaluation. Use random() only during this evaluation;
 * do not retain it, reseed it, or substitute clock/global randomness.
 * Host owns generator scope, algorithm, seed and reproducible progression.
 */
public record StepContext(long generation, RandomGenerator random) {
    public StepContext { Objects.requireNonNull(random); }
}
