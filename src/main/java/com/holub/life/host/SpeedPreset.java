package com.holub.life.host;

/** The original Go menu speeds; each starts playback at its tick interval. */
enum SpeedPreset {
    AGONIZING("Agonizing", 500),
    SLOW("Slow", 150),
    MEDIUM("Medium", 70),
    FAST("Fast", 30);

    final String label;
    final int intervalMillis;

    SpeedPreset(String label, int intervalMillis) {
        this.label = label;
        this.intervalMillis = intervalMillis;
    }
}
