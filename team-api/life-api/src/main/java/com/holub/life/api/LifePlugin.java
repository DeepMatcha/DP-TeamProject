package com.holub.life.api;
/** Boot-time plugin loading; hot unload is NOT in this contract. */
public interface LifePlugin {
    String id();
    void register(PluginRegistrar registrar);
}
