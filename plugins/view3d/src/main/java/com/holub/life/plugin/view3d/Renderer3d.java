package com.holub.life.plugin.view3d;

/** The drawing side of the 3D view, so the wiring of the view can be tested without a graphics window. */
public interface Renderer3d {
    /** Opens the window; throws if 3D graphics cannot be started on this machine. */
    void start();
    /** Replaces what is shown. May be called from any thread. */
    void show(Scene scene);
    void stop();
}
