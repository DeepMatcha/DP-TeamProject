package com.holub.life.plugin.view3d;

import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/** Registers the watch-only 3D view. The other plugins it knows how to draw are optional. */
public final class View3dPlugin implements LifePlugin {
    public static final String ID = "view3d";

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        registrar.registerView(new Board3dView());
    }
}
