package com.holub.life.api;
/** Only ONE accessor factory is active in the v1 simulation plan. */
public interface AccessorFactory<T extends Coordinate> {
    String id();
    Class<T> coordinateType();
    Accessor<T> bind(BoardState snapshot);
}
