package com.holub.life.api;
/** View subscribes here; host publishes only fully committed immutable boards. */
public interface ModelPort {
    BoardState snapshot();
    void addListener(ModelListener listener);
    void removeListener(ModelListener listener);
}
