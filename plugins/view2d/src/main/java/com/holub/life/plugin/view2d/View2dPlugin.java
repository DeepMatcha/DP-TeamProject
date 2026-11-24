package com.holub.life.plugin.view2d;

import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/**
 * Library-style plugin: one board view plus the {@link CellPainters} registry
 * that type-owning plugins depend on to become visible.
 */
public final class View2dPlugin implements LifePlugin {
    public static final String ID = "view2d";

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        registrar.registerView(new Board2dView());
    }
}
