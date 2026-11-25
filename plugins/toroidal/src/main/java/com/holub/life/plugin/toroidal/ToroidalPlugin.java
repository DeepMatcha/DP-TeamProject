package com.holub.life.plugin.toroidal;

import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/** Offers the wrap-around accessor. It takes effect when a plan selects {@link ToroidalAccessor#ID}. */
public final class ToroidalPlugin implements LifePlugin {
    public static final String ID = "toroidal";

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        registrar.registerAccessor(new ToroidalAccessor());
    }
}
