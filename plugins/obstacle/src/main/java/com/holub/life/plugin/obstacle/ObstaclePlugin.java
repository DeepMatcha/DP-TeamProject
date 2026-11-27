package com.holub.life.plugin.obstacle;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/**
 * Walls: one cell type, a Conway rule that respects walls, and a click tool to build them.
 * The rule takes the place of the host's basic Conway stage while this plugin is enabled.
 * Requires the 2D view library plugin; without it this plugin logs why and registers nothing.
 */
public final class ObstaclePlugin implements LifePlugin {
    public static final String ID = "obstacle";
    private static final String VIEW_LIBRARY_CLASS = "com.holub.life.plugin.view2d.CellPainters";
    private static final System.Logger LOG = System.getLogger(ObstaclePlugin.class.getName());

    private final String requiredClass;

    public ObstaclePlugin() { this(VIEW_LIBRARY_CLASS); }

    /** For tests: lets the dependency check look for a class that is not there. */
    ObstaclePlugin(String requiredClass) { this.requiredClass = requiredClass; }

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        if (!isPresent(requiredClass)) {
            LOG.log(System.Logger.Level.WARNING,
                    "Plugin ''{0}'' is disabled: it needs the 2D view library plugin ''view2d'', which is not installed.", ID);
            return;
        }
        CellTypeLookup types = registrar.cellTypes();
        registrar.registerCellType(ObstacleType.INSTANCE);
        registrar.registerLifecycle(new ObstacleConwayRule(types));
        // The wall-aware rule is a whole evolution rule, so the built-in Conway stage must not run as well.
        registrar.replaceDefault(ApiPoint.LIFECYCLE);
        registrar.registerInputTool(new WallTool(types));
        ObstaclePainters.register();
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, ObstaclePlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
