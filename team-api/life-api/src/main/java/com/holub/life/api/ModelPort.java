package com.holub.life.api;
/** View subscribes here; host owns mutation and event dispatch. */
public interface ModelPort {
    BoardState snapshot();
    void addListener(ModelListener listener);
    void removeListener(ModelListener listener);
}
