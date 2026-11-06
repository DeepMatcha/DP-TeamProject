package com.holub.life.api;
public interface ViewPlugin {
    String id();
    void attach(ModelPort model, ControllerPort controller);
    void detach();
}
