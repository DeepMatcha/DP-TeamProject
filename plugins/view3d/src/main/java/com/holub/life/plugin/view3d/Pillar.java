package com.holub.life.plugin.view3d;

/** How one cell stands on the board: a colored column, optionally glowing. Engine-independent. */
public record Pillar(float height, float red, float green, float blue, Glow glow) {
    public enum Glow { NONE, STEADY, PULSE }

    public static Pillar of(float height, int rgb, Glow glow) {
        return new Pillar(height, (rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, glow);
    }
}
