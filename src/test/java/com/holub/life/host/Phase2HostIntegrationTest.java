package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises Phase 2 service providers through the real Phase 1 session and engine. */
class Phase2HostIntegrationTest {
    private static final Set<String> PHASE2_IDS =
            Set.of("codec", "foodaging", "obstacle", "toroidal", "view2d", "view3d");
    private static final String BOUNDED = "core:bounded";
    private static final String WRAP = "toroidal:wrap";
    private static final String AGE = "foodaging:age";
    private static final String FOOD = "foodaging:food";
    private static final String WALL = "obstacle:wall";
    private static final String WALL_TOOL = "obstacle:toggle-wall";
    private static final CellType LIVING_EXTENSION = new CellType("test:living-extension") {
        @Override
        public boolean isAlive(CellState state) {
            return true;
        }
    };

    @Test
    void discoversProvidersAndRegistersTheirCatalogWithoutOpeningViews() {
        List<LifePlugin> providers = discover();
        var discovered = providers.stream().map(LifePlugin::id).collect(Collectors.toSet());
        assertTrue(discovered.containsAll(PHASE2_IDS));
        try (var host = CoreGame.newSession(new BoardSize(2, 2), 42, providers)) {
            var registry = host.registry();
            assertTrue(registry.view("view2d:board").isPresent());
            assertTrue(registry.view("view3d:board").isPresent());
            assertTrue(registry.inputTool(WALL_TOOL).isPresent());
            assertTrue(registry.accessor(WRAP).isPresent());
            assertTrue(registry.snapshotCodec("codec:json").isPresent());
            assertTrue(registry.snapshotCodec("codec:binary").isPresent());
            assertTrue(registry.resolve(FOOD).isPresent());
        }
    }

    @Test
    void enabledObstacleReplacesConwayAndDisablingItRestoresTheDefaultRule() throws Exception {
        var plugins = new ArrayList<>(discover());
        plugins.add(new LifePlugin() {
            @Override
            public String id() {
                return "test:extra-cell";
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registrar.registerCellType(LIVING_EXTENSION);
            }
        });

        try (var host = CoreGame.newSession(new BoardSize(7, 7), 42, plugins)) {
            GridPos wallPosition = new GridPos(3, 2);
            GridPos isolatedExtension = new GridPos(0, 6);
            await(host.configure(select(host, BOUNDED, "obstacle")));
            await(host.applyTool(WALL_TOOL, new ToolInput(wallPosition, "click", Map.of())));
            putLive(host, new GridPos(2, 3), new GridPos(3, 3), new GridPos(4, 3));
            await(host.apply(new BoardPatch(List.of(new CellEdit(isolatedExtension,
                    new CellState(LIVING_EXTENSION, Map.of()))))));
            var checkpoint = await(host.session());

            BoardState obstacleTick = await(host.step());
            // The wall prevents one birth. The resulting pair would die if both stages ran.
            assertEquals(Set.of(new GridPos(3, 3), new GridPos(3, 4)), livePositions(obstacleTick));
            assertSame(registered(host, WALL), obstacleTick.cellAt(wallPosition).type());
            assertSame(CoreCellTypes.empty(host.registry()), obstacleTick.cellAt(isolatedExtension).type());

            await(host.restore(checkpoint));
            await(host.configure(select(host, BOUNDED)));
            BoardState defaultTick = await(host.step());
            // The core rule leaves this extension alone. The pair proves the core stage ran.
            assertEquals(Set.of(new GridPos(3, 3), new GridPos(3, 4), isolatedExtension),
                    livePositions(defaultTick));
        }
    }

    @Test
    void selectingTheRealToroidalAccessorMakesAnEdgeBirthWrap() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(5, 5), 42, discover())) {
            await(host.configure(select(host, BOUNDED)));
            putLive(host, new GridPos(1, 0), new GridPos(2, 0), new GridPos(3, 0));
            var checkpoint = await(host.session());
            assertEquals(Set.of(new GridPos(2, 0), new GridPos(2, 1)), livePositions(await(host.step())));

            await(host.restore(checkpoint));
            await(host.configure(select(host, WRAP, "toroidal")));
            assertEquals(Set.of(new GridPos(2, 0), new GridPos(2, 1), new GridPos(2, 4)),
                    livePositions(await(host.step())));
        }
    }

    @Test
    void feedingResetsAgeBeforeFoodIsEatenAndSurvivorsKeepTheirAges() throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(5, 5), 42, discover())) {
            await(host.configure(select(host, BOUNDED, "foodaging")));
            putAgedBlockAndFood(host);

            BoardState fed = await(host.step());
            assertEquals("0", fed.cellAt(new GridPos(1, 1)).properties().get(AGE));
            assertEquals("5", fed.cellAt(new GridPos(2, 2)).properties().get(AGE));
            assertSame(CoreCellTypes.empty(host.registry()), fed.cellAt(new GridPos(0, 0)).type());

            BoardState next = await(host.step());
            assertEquals("1", next.cellAt(new GridPos(1, 1)).properties().get(AGE));
            assertEquals("6", next.cellAt(new GridPos(2, 2)).properties().get(AGE));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"codec:json", "codec:binary"})
    void savesAndRestoresTheRealSessionIncludingItsNextStep(String codecId) throws Exception {
        try (var host = CoreGame.newSession(new BoardSize(5, 5), 42, discover())) {
            await(host.configure(select(host, WRAP, "toroidal", "foodaging", "codec")));
            putAgedBlockAndFood(host);
            var checkpoint = await(host.session());
            var codec = host.registry().snapshotCodec(codecId).orElseThrow();
            var bytes = new ByteArrayOutputStream();
            codec.save(checkpoint, bytes, host.registry());
            var loaded = codec.load(new ByteArrayInputStream(bytes.toByteArray()), host.registry());

            assertEquals(checkpoint, loaded);
            assertSame(registered(host, FOOD), loaded.board().cellAt(new GridPos(0, 0)).type());
            BoardState next = await(host.step());
            await(host.configure(select(host, BOUNDED)));
            await(host.restore(loaded));

            assertEquals(checkpoint, await(host.session()));
            assertEquals(next, await(host.step()));
        }
    }

    private static List<LifePlugin> discover() {
        return ServiceLoader.load(LifePlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(LifePlugin::id))
                .toList();
    }

    private static SimulationPlan select(HostController host, String accessor, String... enabled) throws Exception {
        SimulationPlan current = await(host.session()).plan();
        var selections = new ArrayList<PluginSelection>();
        current.plugins().stream()
                .filter(plugin -> !PHASE2_IDS.contains(plugin.pluginId()))
                .forEach(selections::add);
        for (String id : enabled) {
            selections.add(new PluginSelection(id, true));
        }
        PHASE2_IDS.stream().sorted()
                .filter(id -> !List.of(enabled).contains(id))
                .map(id -> new PluginSelection(id, false))
                .forEach(selections::add);
        return new SimulationPlan(accessor, selections, Map.of());
    }

    private static CellType registered(HostController host, String id) {
        return host.registry().resolve(id).orElseThrow();
    }

    private static void putLive(HostController host, GridPos... positions) throws Exception {
        var alive = new CellState(CoreCellTypes.alive(host.registry()), Map.of());
        var edits = Arrays.stream(positions).map(position -> new CellEdit(position, alive)).toList();
        await(host.apply(new BoardPatch(edits)));
    }

    private static void putAgedBlockAndFood(HostController host) throws Exception {
        var alive = new CellState(CoreCellTypes.alive(host.registry()), Map.of(AGE, "4"));
        var food = new CellState(registered(host, FOOD), Map.of());
        await(host.apply(new BoardPatch(List.of(
                new CellEdit(new GridPos(1, 1), alive),
                new CellEdit(new GridPos(2, 1), alive),
                new CellEdit(new GridPos(1, 2), alive),
                new CellEdit(new GridPos(2, 2), alive),
                new CellEdit(new GridPos(0, 0), food)))));
    }

    private static Set<GridPos> livePositions(BoardState board) {
        var living = new HashSet<GridPos>();
        for (int y = 0; y < board.size().height(); y++) {
            for (int x = 0; x < board.size().width(); x++) {
                var position = new GridPos(x, y);
                CellState cell = board.cellAt(position);
                if (cell.type().isAlive(cell)) {
                    living.add(position);
                }
            }
        }
        return living;
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
