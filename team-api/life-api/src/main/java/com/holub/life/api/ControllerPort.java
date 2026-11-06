package com.holub.life.api;
/** View input goes here. Controller validates patch and updates Model. */
public interface ControllerPort {
    void step();
    void apply(BoardPatch patch);
    void setRunning(boolean running);
}
