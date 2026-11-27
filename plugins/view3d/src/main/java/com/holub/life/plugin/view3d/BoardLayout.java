package com.holub.life.plugin.view3d;

import com.holub.life.api.BoardSize;

/**
 * Where a board cell sits in space: on a flat floor, or wrapped on a torus when the board itself wraps.
 * Plain math without engine types, so it can be checked in JUnit.
 */
final class BoardLayout {
    /** Cell base center, the three unit directions of its column, and its footprint along the two board axes. */
    record Frame(float[] origin, float[] alongX, float[] up, float[] alongY, float sizeX, float sizeY) {}

    private static final float TUBE_CELL = 0.75f;

    private final BoardSize size;
    private final boolean torus;
    private final float ringRadius;
    private final float tubeRadius;

    BoardLayout(BoardSize size, boolean torus) {
        this.size = size;
        this.torus = torus;
        this.tubeRadius = (float) (TUBE_CELL * size.height() / (2 * Math.PI));
        // The ring must be clearly wider than the tube, or the hole in the middle closes.
        this.ringRadius = Math.max((float) (size.width() / (2 * Math.PI)), 2 * tubeRadius);
    }

    boolean torus() { return torus; }
    float ringRadius() { return ringRadius; }
    float tubeRadius() { return tubeRadius; }

    /** Radius of a sphere around the origin that contains the whole board. */
    float extent() {
        return torus ? ringRadius + tubeRadius : (float) Math.hypot(size.width(), size.height()) / 2;
    }

    Frame frame(int x, int y) {
        if (!torus)
            return new Frame(new float[] {x + 0.5f - size.width() / 2f, 0, y + 0.5f - size.height() / 2f},
                    new float[] {1, 0, 0}, new float[] {0, 1, 0}, new float[] {0, 0, 1}, 1, 1);

        double around = 2 * Math.PI * (x + 0.5) / size.width();   // angle around the ring
        double across = 2 * Math.PI * (y + 0.5) / size.height();  // angle around the tube
        float cosA = (float) Math.cos(around), sinA = (float) Math.sin(around);
        float cosT = (float) Math.cos(across), sinT = (float) Math.sin(across);
        float fromAxis = ringRadius + tubeRadius * cosT;
        return new Frame(new float[] {fromAxis * cosA, tubeRadius * sinT, fromAxis * sinA},
                new float[] {-sinA, 0, cosA},
                new float[] {cosT * cosA, sinT, cosT * sinA},
                new float[] {-sinT * cosA, cosT, -sinT * sinA},
                (float) (fromAxis * 2 * Math.PI / size.width()), TUBE_CELL);
    }
}
