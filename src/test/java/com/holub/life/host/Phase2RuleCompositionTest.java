package com.holub.life.host;

import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PlaybackState;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Checks the boundaries between the real host, real rules, and snapshot loading. */
class Phase2RuleCompositionTest {
    private static final BoardSize SIZE = new BoardSize(5, 5);
    private static final String BOUNDED = "core:bounded";
    private static final String WRAP = "toroidal:wrap";
    private static final String JSON = "codec:json";

    @Test
    void intactFoodDependsOnTheOrderOfRealObstacleAndFoodAgingStages() throws Exception {
        var resident = new GridPos(2, 2);
        var meal = new GridPos(2, 1);
        var wall = new GridPos(4, 4);
        try (var host = CoreGame.newSession(SIZE, 42, discover())) {
            CellType alive = CoreCellTypes.alive(host.registry());
            CellType empty = CoreCellTypes.empty(host.registry());
            CellType food = host.registry().resolve("foodaging:food").orElseThrow();
            CellType obstacle = host.registry().resolve("obstacle:wall").orElseThrow();
            var aged = new CellState(alive, Map.of("foodaging:age", "17"));
            await(host.apply(new BoardPatch(List.of(
                    new CellEdit(resident, aged),
                    new CellEdit(meal, new CellState(food, Map.of())),
                    new CellEdit(wall, new CellState(obstacle, Map.of()))))));
            var checkpoint = await(host.session());

            await(host.configure(withPlugins(checkpoint.plan(), BOUNDED, "obstacle", "foodaging")));
            var obstacleFirst = await(host.step());
            // Obstacle Conway kills the isolated resident before FoodRule can see an eater.
            assertSame(empty, obstacleFirst.cellAt(resident).type());
            assertSame(food, obstacleFirst.cellAt(meal).type());
            assertSame(obstacle, obstacleFirst.cellAt(wall).type());
            assertEquals(1L, obstacleFirst.generation());
            assertEquals(42L, obstacleFirst.seed());
            assertEquals(aged, checkpoint.board().cellAt(resident));
            assertSame(food, checkpoint.board().cellAt(meal).type());

            await(host.restore(checkpoint));
            await(host.configure(withPlugins(checkpoint.plan(), BOUNDED, "foodaging", "obstacle")));
            var foodFirst = await(host.step());
            // FoodRule sees the living resident and consumes the meal before Conway kills it.
            assertSame(empty, foodFirst.cellAt(resident).type());
            assertSame(empty, foodFirst.cellAt(meal).type());
            assertSame(obstacle, foodFirst.cellAt(wall).type());
            assertEquals(1L, foodFirst.generation());
            assertEquals(42L, foodFirst.seed());
        }
    }

    @Test
    void decodedSnapshotWithAnUnavailableProviderPreservesThePlayingSession() throws Exception {
        List<LifePlugin> providers = discover();
        List<LifePlugin> reduced = providers.stream()
                .filter(plugin -> !plugin.id().equals("foodaging"))
                .toList();
        try (var source = CoreGame.newSession(SIZE, 42, providers);
             var target = CoreGame.newSession(SIZE, 71, reduced)) {
            // Use core-only cells so decoding succeeds even though the target has no Food/Aging provider.
            await(source.step());
            placeLiveCell(source, new GridPos(0, 1));
            var sourcePlan = withPlugins(await(source.session()).plan(), BOUNDED, "foodaging", "codec");
            await(source.configure(sourcePlan));
            var exported = await(source.session());
            var bytes = new ByteArrayOutputStream();
            source.registry().snapshotCodec(JSON).orElseThrow().save(exported, bytes, source.registry());
            var decoded = target.registry().snapshotCodec(JSON).orElseThrow()
                    .load(new ByteArrayInputStream(bytes.toByteArray()), target.registry());
            assertEquals(exported, decoded);
            assertSame(CoreCellTypes.alive(target.registry()), decoded.board().cellAt(new GridPos(0, 1)).type());

            placeLiveCell(target, new GridPos(4, 3));
            var targetPlan = withPlugins(await(target.session()).plan(), WRAP, "toroidal", "obstacle");
            await(target.configure(targetPlan));
            // Leave the timer armed without letting a normal tick race the restore.
            await(target.setTickIntervalMillis(60_000));
            await(target.setRunning(true));
            var notifications = new AtomicInteger();
            target.model().addListener(board -> notifications.incrementAndGet());
            var before = await(target.session());
            assertNotEquals(decoded.board(), before.board());
            assertNotEquals(decoded.plan(), before.plan());

            var failure = assertThrows(ExecutionException.class, () -> await(target.restore(decoded)));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            assertEquals(before, await(target.session()));
            assertSame(before.board(), target.model().snapshot());
            assertEquals(new PlaybackState(true, 60_000), await(target.playbackState()));
            assertEquals(0, notifications.get());
        }
    }

    private static List<LifePlugin> discover() {
        return ServiceLoader.load(LifePlugin.class).stream().map(ServiceLoader.Provider::get).toList();
    }

    private static SimulationPlan withPlugins(SimulationPlan corePlan, String accessor, String... ids) {
        var selections = new ArrayList<>(corePlan.plugins());
        for (String id : ids) {
            selections.add(new PluginSelection(id, true));
        }
        return new SimulationPlan(accessor, selections, Map.of());
    }

    private static void placeLiveCell(HostController host, GridPos position) throws Exception {
        var state = new CellState(CoreCellTypes.alive(host.registry()), Map.of());
        await(host.apply(new BoardPatch(List.of(new CellEdit(position, state)))));
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }
}
