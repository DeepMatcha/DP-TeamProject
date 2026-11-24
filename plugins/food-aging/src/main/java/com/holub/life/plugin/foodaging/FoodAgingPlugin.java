package com.holub.life.plugin.foodaging;

import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;

/**
 * Food and aging as one plugin: two cell types and two whole-board stages
 * (aging first, then food, so that a cell is fed and its food is eaten in the same tick).
 * Requires the 2D view library plugin; without it this plugin logs why and registers nothing.
 */
public final class FoodAgingPlugin implements LifePlugin {
    public static final String ID = "foodaging";
    private static final String VIEW_LIBRARY_CLASS = "com.holub.life.plugin.view2d.CellPainters";
    private static final System.Logger LOG = System.getLogger(FoodAgingPlugin.class.getName());

    private final String requiredClass;

    public FoodAgingPlugin() { this(VIEW_LIBRARY_CLASS); }

    /** For tests: lets the dependency check look for a class that is not there. */
    FoodAgingPlugin(String requiredClass) { this.requiredClass = requiredClass; }

    @Override public String id() { return ID; }

    @Override public void register(PluginRegistrar registrar) {
        if (!isPresent(requiredClass)) {
            LOG.log(System.Logger.Level.WARNING,
                    "Plugin ''{0}'' is disabled: it needs the 2D view library plugin ''view2d'', which is not installed.", ID);
            return;
        }
        CellTypeLookup types = registrar.cellTypes();
        registrar.registerCellType(FoodType.INSTANCE);
        registrar.registerCellType(BurstSeedType.INSTANCE);
        registrar.registerLifecycle(new AgingRule(types, AgingRule.DEFAULT_MAX_AGE));
        registrar.registerLifecycle(new FoodRule(types, FoodRule.DEFAULT_SEED_CHANCE,
                FoodRule.DEFAULT_BURST_CHANCE, FoodRule.DEFAULT_BURST_RADIUS));
        FoodAgingPainters.register(CoreCellTypes.alive(types), AgingRule.DEFAULT_MAX_AGE);
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, FoodAgingPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
