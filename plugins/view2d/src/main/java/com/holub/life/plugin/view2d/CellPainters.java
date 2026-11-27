package com.holub.life.plugin.view2d;

import com.holub.life.api.CellState;
import com.holub.life.api.CellType;

import java.awt.Color;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where plugins that own a cell type tell this view how to draw it.
 * Types are matched by object identity, like everywhere else at runtime.
 * A type without a painter is drawn from isAlive() alone.
 */
public final class CellPainters {
    public static final Color DEFAULT_ALIVE = new Color(0xD03030);
    public static final Color DEFAULT_DEAD = new Color(0xFFFFE0);

    private static final Map<CellType, CellPainter> PAINTERS = new ConcurrentHashMap<>();

    private CellPainters() {}

    /** A later registration for the same type replaces the earlier one. */
    public static void register(CellType type, CellPainter painter) {
        PAINTERS.put(Objects.requireNonNull(type), Objects.requireNonNull(painter));
    }

    public static void unregister(CellType type) { PAINTERS.remove(type); }

    public static Color colorOf(CellState state) {
        CellPainter painter = PAINTERS.get(state.type());
        if (painter != null) return painter.colorOf(state);
        return state.type().isAlive(state) ? DEFAULT_ALIVE : DEFAULT_DEAD;
    }
}
