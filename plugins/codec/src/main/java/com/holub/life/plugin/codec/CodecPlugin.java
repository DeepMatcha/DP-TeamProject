package com.holub.life.plugin.codec;

import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/** Two full-snapshot formats that keep extension types and per-cell properties: JSON and binary. */
public final class CodecPlugin implements LifePlugin {
    public static final String ID = "codec";

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        registrar.registerSnapshotCodec(new JsonSnapshotCodec());
        registrar.registerSnapshotCodec(new BinarySnapshotCodec());
    }
}
