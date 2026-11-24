package com.holub.life.plugin.foodaging;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.plugin.view2d.CellPainters;
import com.holub.life.plugin.view2d.View2dPlugin;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodAgingPluginTest {
    private static FakeHost hostWithPlugin() {
        return new FakeHost().load(new View2dPlugin()).load(new FoodAgingPlugin());
    }

    @Test
    void registersItsTypesByIdAndTwoStagesInOrder() {
        FakeHost host = hostWithPlugin();

        assertSame(FoodType.INSTANCE, host.resolve("foodaging:food").orElseThrow());
        assertSame(BurstSeedType.INSTANCE, host.resolve("foodaging:burst-seed").orElseThrow());
        assertEquals("foodaging:food", host.idOf(FoodType.INSTANCE).orElseThrow());
        assertInstanceOf(AgingRule.class, host.lifecycles().get(0));
        assertInstanceOf(FoodRule.class, host.lifecycles().get(1));
        List<RegistrationInfo> stages = host.registrations().stream()
                .filter(registration -> registration.point() == ApiPoint.LIFECYCLE).toList();
        assertEquals(List.of(new RegistrationInfo("foodaging", ApiPoint.LIFECYCLE, "foodaging#lifecycle/0", 0),
                new RegistrationInfo("foodaging", ApiPoint.LIFECYCLE, "foodaging#lifecycle/1", 1)), stages);
    }

    @Test
    void cellIsFedAndItsFoodIsEatenInTheSameTick() {
        FakeHost host = hostWithPlugin();
        CellState old = new CellState(FakeHost.ALIVE, Map.of(AgingRule.AGE, "9"));
        BoardState board = Boards.parse(Map.of('o', old, 'f', FoodType.STATE), "of");

        BoardState next = host.step(board);

        assertEquals(0, AgingRule.ageOf(Boards.at(next, 0, 0)), "fed: age reset");
        assertSame(FakeHost.EMPTY, Boards.at(next, 1, 0).type(), "the food is gone");
        assertEquals(1, next.generation(), "two stages, one generation");
    }

    @Test
    void stepIsReproducibleFromTheSameBoard() {
        FakeHost host = hostWithPlugin();
        BoardState source = Boards.withSeed(Boards.parse("#..#", "....", ".##.", "...."), 99);

        assertEquals(host.step(host.step(source)), host.step(host.step(source)));
    }

    @Test
    void disablesItselfWhenTheViewLibraryIsMissing() {
        FakeHost host = new FakeHost().load(new FoodAgingPlugin("no.such.ViewLibrary"));

        assertTrue(host.registrations().isEmpty(), "nothing was registered");
        assertTrue(host.resolve("foodaging:food").isEmpty());
        assertTrue(host.lifecycles().isEmpty());
    }

    @Test
    void cellsAreVisibleInTheTwoDimensionalView() {
        hostWithPlugin();
        CellState young = new CellState(FakeHost.ALIVE, Map.of(AgingRule.AGE, "0"));
        CellState old = new CellState(FakeHost.ALIVE, Map.of(AgingRule.AGE, "20"));

        assertEquals(FoodAgingPainters.FOOD, CellPainters.colorOf(FoodType.STATE));
        assertEquals(FoodAgingPainters.BURST_SEED, CellPainters.colorOf(BurstSeedType.STATE));
        assertEquals(FoodAgingPainters.YOUNG, CellPainters.colorOf(young));
        assertEquals(FoodAgingPainters.OLD, CellPainters.colorOf(old));
        assertNotEquals(CellPainters.colorOf(young), CellPainters.colorOf(old), "age is visible");
    }
}
