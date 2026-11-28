package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.BoardState;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plan arithmetic of switching a provider, without a controller, thread or view. */
class PluginSwitchTest {
    private static final String BASE = "base";
    private static final String LOOK = "look";
    private static final String WRAP = "wrap";
    private static final String WRAP_ACCESSOR = "wrap:first";

    private final HostRegistry registry = new HostRegistry(List.of(
            plugin(BASE, registrar -> registrar.registerAccessor(new BoundedAccessor())),
            plugin(LOOK, registrar -> {}),
            plugin(WRAP, registrar -> {
                registrar.registerAccessor(new NamedAccessor(WRAP_ACCESSOR));
                registrar.registerAccessor(new NamedAccessor("wrap:second"));
            })));
    private final PluginSwitch pluginSwitch = new PluginSwitch(registry, BoundedAccessor.ID);
    private final SimulationPlan start = new SimulationPlan(BoundedAccessor.ID,
            List.of(new PluginSelection(BASE, true)), Map.of("base", Map.of()));

    @Test
    void enablingInsertsTheProviderAtItsCatalogPosition() {
        var withWrap = pluginSwitch.switched(start, WRAP, true).orElseThrow();
        var withBoth = pluginSwitch.switched(withWrap, LOOK, true).orElseThrow();

        assertEquals(List.of(new PluginSelection(BASE, true), new PluginSelection(LOOK, true),
                new PluginSelection(WRAP, true)), withBoth.plugins());
        assertEquals(start.pluginOptions(), withBoth.pluginOptions());
    }

    @Test
    void enablingAnAccessorProviderSelectsItsFirstAccessor() {
        assertEquals(WRAP_ACCESSOR, pluginSwitch.switched(start, WRAP, true).orElseThrow().accessorId());
    }

    @Test
    void disablingTheActiveAccessorsProviderFallsBackToTheInitialAccessor() {
        var wrapped = pluginSwitch.switched(start, WRAP, true).orElseThrow();

        var unwrapped = pluginSwitch.switched(wrapped, WRAP, false).orElseThrow();

        assertEquals(BoundedAccessor.ID, unwrapped.accessorId());
        assertEquals(List.of(new PluginSelection(BASE, true), new PluginSelection(WRAP, false)), unwrapped.plugins());
    }

    @Test
    void anUnchangedStateNeedsNoNewPlan() {
        assertTrue(pluginSwitch.switched(start, BASE, true).isEmpty());
        assertTrue(pluginSwitch.switched(start, LOOK, false).isEmpty(), "a provider absent from the plan is already off");
    }

    @Test
    void anUnknownProviderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> pluginSwitch.switched(start, "missing", true));
    }

    private static LifePlugin plugin(String id, Consumer<PluginRegistrar> registration) {
        return new LifePlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registration.accept(registrar);
            }
        };
    }

    private record NamedAccessor(String id) implements AccessorFactory<GridPos> {
        @Override
        public Class<GridPos> coordinateType() {
            return GridPos.class;
        }

        @Override
        public Accessor<GridPos> bind(BoardState snapshot) {
            throw new UnsupportedOperationException();
        }
    }
}
