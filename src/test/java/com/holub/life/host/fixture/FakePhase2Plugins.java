package com.holub.life.host.fixture;

import com.holub.life.api.CellState;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.NeighborBoard;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.StepContext;
import java.util.HashMap;

/** API-only test fixtures for checking stage order and whole-board updates. */
public final class FakePhase2Plugins {
    public static final String MARK_ID = "test:mark";
    public static final String OBSERVE_ID = "test:observe";
    public static final String MARK_PROPERTY = "test:marker";
    public static final String NEIGHBOR_COUNT_PROPERTY = "test:marked-neighbors";

    private FakePhase2Plugins() {}

    /** Marks each center with one draw from the RNG supplied by the host. */
    public static LifePlugin mark() {
        return plugin(MARK_ID, new LifecycleManager(0) {
            @Override
            public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                CellState center = neighbors.center().state();
                var properties = new HashMap<>(center.properties());
                properties.put(MARK_PROPERTY, Long.toString(context.random().nextLong()));
                return new CellState(center.type(), properties);
            }
        });
    }

    /** Counts marked neighbors in the radius-one snapshot supplied by the host. */
    public static LifePlugin observe() {
        return plugin(OBSERVE_ID, new LifecycleManager(1) {
            @Override
            public CellState figureNextState(NeighborBoard neighbors, StepContext context) {
                long count = neighbors.cells().stream()
                    .filter(sample -> sample.state().properties().containsKey(MARK_PROPERTY))
                    .count();
                CellState center = neighbors.center().state();
                var properties = new HashMap<>(center.properties());
                properties.put(NEIGHBOR_COUNT_PROPERTY, Long.toString(count));
                return new CellState(center.type(), properties);
            }
        });
    }

    private static LifePlugin plugin(String id, LifecycleManager rule) {
        return new LifePlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registrar.registerLifecycle(rule);
            }
        };
    }
}
