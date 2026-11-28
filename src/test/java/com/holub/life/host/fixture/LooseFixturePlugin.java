package com.holub.life.host.fixture;

import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/** Compiled once for the test, but copied into a loose JAR alongside a separate dependency JAR. */
public final class LooseFixturePlugin implements LifePlugin {
    private final String id;

    public LooseFixturePlugin() {
        id = Dependency.pluginId();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void register(PluginRegistrar registrar) {}

    public static final class Dependency {
        public static String pluginId() {
            return "test:loose-provider";
        }
    }
}
