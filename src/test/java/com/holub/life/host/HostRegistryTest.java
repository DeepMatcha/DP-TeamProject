package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CellTypeLookup;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.ModelPort;
import com.holub.life.api.PluginInfo;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.api.ToolInput;
import com.holub.life.api.ViewPlugin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostRegistryTest {
    @Test
    void preservesRegistrationOrderAndScopesIdsByApiPoint() {
        var firstRule = new CoreConwayRule();
        var secondRule = new CoreConwayRule();
        var otherPluginRule = new CoreConwayRule();
        var accessor = new TestAccessor("demo:shared");
        var tool = new TestTool("demo:shared");
        var metadata = new HashMap<>(Map.of("version", "1.2", "name", "Fixture"));
        var plugins = new ArrayList<>(List.of(
                plugin("first", registrar -> {
                    registrar.registerLifecycle(firstRule);
                    registrar.registerAccessor(accessor);
                    registrar.registerInputTool(tool);
                    registrar.registerLifecycle(secondRule);
                }),
                plugin("second", registrar -> registrar.registerLifecycle(otherPluginRule))));
        var registry = new HostRegistry(plugins, Map.of("first", metadata));

        plugins.clear();
        metadata.clear();

        assertEquals(List.of(
                new PluginInfo("first", Map.of("version", "1.2", "name", "Fixture")),
                new PluginInfo("second", Map.of())), registry.plugins());
        assertEquals(List.of(
                new RegistrationInfo("first", ApiPoint.LIFECYCLE, "first#lifecycle/0", 0),
                new RegistrationInfo("first", ApiPoint.ACCESSOR, "demo:shared", 0),
                new RegistrationInfo("first", ApiPoint.INPUT_TOOL, "demo:shared", 0),
                new RegistrationInfo("first", ApiPoint.LIFECYCLE, "first#lifecycle/1", 1),
                new RegistrationInfo("second", ApiPoint.LIFECYCLE, "second#lifecycle/0", 0)),
                registry.registrations());
        assertEquals(List.of(firstRule, secondRule), registry.lifecycleStages("first"));
        assertEquals(List.of(otherPluginRule), registry.lifecycleStages("second"));
        assertEquals(Map.of("first", "1.2"), registry.pluginVersions());
        assertSame(accessor, registry.accessor("demo:shared").orElseThrow());
        assertSame(tool, registry.inputTool("demo:shared").orElseThrow());
        assertTrue(registry.accessor("absent").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.plugins().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.registrations().clear());
        assertThrows(UnsupportedOperationException.class, () -> registry.lifecycleStages("first").clear());
    }

    @Test
    void mapsCellIdsOnlyForTheExactRegisteredSingleton() {
        var registry = new HostRegistry(List.of());
        var impostor = new CellType("core:alive") {
            @Override
            public boolean isAlive(CellState state) {
                return true;
            }
        };

        assertSame(CoreCells.ALIVE, registry.resolve("core:alive").orElseThrow());
        assertSame(CoreCells.EMPTY, registry.resolve("core:empty").orElseThrow());
        assertEquals(Optional.of("core:alive"), registry.idOf(CoreCells.ALIVE));
        assertTrue(registry.idOf(impostor).isEmpty());
        assertTrue(registry.resolve("absent").isEmpty());
    }

    @Test
    void coreTypesAreAvailableBeforeRegistrationAndLookupStaysLive() {
        var lookup = new AtomicReference<CellTypeLookup>();
        var extension = new CellType("test:extension") {
            @Override
            public boolean isAlive(CellState state) {
                return false;
            }
        };
        var reader = plugin("test:reader", registrar -> {
            lookup.set(registrar.cellTypes());
            assertSame(CoreCells.EMPTY, CoreCellTypes.empty(lookup.get()));
            assertSame(CoreCells.ALIVE, CoreCellTypes.alive(lookup.get()));
            assertTrue(lookup.get().resolve(extension.id()).isEmpty());
        });
        var writer = plugin("test:writer", registrar -> registrar.registerCellType(extension));
        var registry = new HostRegistry(List.of(reader, writer));

        assertSame(extension, lookup.get().resolve(extension.id()).orElseThrow());
        assertEquals(Optional.of(extension.id()), lookup.get().idOf(extension));
        assertSame(extension, registry.resolve(extension.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new HostRegistry(List.of(plugin("test:duplicate",
            registrar -> registrar.registerCellType(CoreCells.ALIVE)))));
    }

    @Test
    void recordsAViewReplacementThatRegistersItsView() {
        var view = new TestView("test:view");
        var plugin = plugin("test:replacement", registrar -> {
            registrar.replaceDefault(ApiPoint.VIEW);
            registrar.registerView(view);
        });
        var registry = new HostRegistry(List.of(plugin));

        assertTrue(registry.replacesDefault("test:replacement", ApiPoint.VIEW));
        assertSame(view, registry.view(view.id()).orElseThrow());
    }

    @Test
    void requiresASupportedDefaultAndARegisteredReplacement() {
        for (ApiPoint point : List.of(ApiPoint.CELL_TYPE, ApiPoint.LIFECYCLE, ApiPoint.VIEW)) {
            var invalid = plugin("test:replacement", registrar -> registrar.replaceDefault(point));
            assertThrows(IllegalArgumentException.class, () -> new HostRegistry(List.of(invalid)));
        }
    }

    @Test
    void rejectsDuplicatePluginAndModuleIds() {
        var first = plugin("first", registrar -> {
            registrar.registerAccessor(new TestAccessor("demo:accessor"));
        });
        var second = plugin("second", registrar -> {
            registrar.registerAccessor(new TestAccessor("demo:accessor"));
        });

        assertThrows(IllegalArgumentException.class, () -> new HostRegistry(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> new HostRegistry(List.of(first, second)));
    }

    @Test
    void closesTheRegistrationScopeAfterStartup() {
        var retained = new AtomicReference<PluginRegistrar>();
        var registry = new HostRegistry(List.of(plugin("test", retained::set)));

        var extension = new CellType("test:late") {
            @Override
            public boolean isAlive(CellState state) {
                return false;
            }
        };
        assertThrows(IllegalStateException.class, () -> retained.get().registerCellType(extension));
        assertTrue(registry.resolve(extension.id()).isEmpty());
        assertTrue(registry.registrations().isEmpty());
    }

    private static LifePlugin plugin(String id, Consumer<PluginRegistrar> register) {
        return new LifePlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void register(PluginRegistrar registrar) {
                register.accept(registrar);
            }
        };
    }

    private record TestAccessor(String id) implements AccessorFactory<GridPos> {
        @Override
        public Class<GridPos> coordinateType() {
            return GridPos.class;
        }

        @Override
        public Accessor<GridPos> bind(BoardState snapshot) {
            throw new UnsupportedOperationException();
        }
    }

    private record TestView(String id) implements ViewPlugin {
        @Override
        public void attach(ModelPort model, ControllerPort controller) {}

        @Override
        public void detach() {}
    }

    private record TestTool(String id) implements InputTool {
        @Override
        public BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor) {
            throw new UnsupportedOperationException();
        }
    }
}
