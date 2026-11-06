package com.holub.life.api;
/** Canonical position + immutable state, returned by an Accessor. */
public record CellSample(GridPos canonicalPos, CellState state) {}
