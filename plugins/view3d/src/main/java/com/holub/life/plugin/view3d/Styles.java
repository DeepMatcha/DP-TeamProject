package com.holub.life.plugin.view3d;

import com.holub.life.api.CellState;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.RegistryView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Chooses the styles from what is actually registered in the host, so the 3D view
 * looks different depending on which plugins are installed.
 */
final class Styles {
    static final Pillar PLAIN_ALIVE = Pillar.of(1.0f, 0xD03030, Pillar.Glow.NONE);
    /** Without any plugin style: a living cell is a plain column, anything else is bare floor. */
    static final CellStyle DEFAULT =
            state -> state.type().isAlive(state) ? Optional.of(PLAIN_ALIVE) : Optional.empty();

    private Styles() {}

    /** Plugin styles first, the default last. A type ID is registered only if the classes of its plugin are present. */
    static List<CellStyle> detect(RegistryView registry) {
        List<CellStyle> styles = new ArrayList<>();
        if (registry.resolve("foodaging:food").isPresent())
            styles.add(new FoodAgingStyle(CoreCellTypes.alive(registry)));
        if (registry.resolve("obstacle:wall").isPresent())
            styles.add(new ObstacleStyle());
        styles.add(DEFAULT);
        return List.copyOf(styles);
    }

    static Optional<Pillar> pillarOf(List<CellStyle> styles, CellState state) {
        for (CellStyle style : styles) {
            Optional<Pillar> pillar = style.pillarOf(state);
            if (pillar.isPresent()) return pillar;
        }
        return Optional.empty();
    }
}
