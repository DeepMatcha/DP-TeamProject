package com.holub.life.plugin.view3d;

import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.plugin.foodaging.AgingRule;
import com.holub.life.plugin.foodaging.BurstSeedType;
import com.holub.life.plugin.foodaging.FoodAgingPlugin;
import com.holub.life.plugin.foodaging.FoodType;
import com.holub.life.plugin.obstacle.ObstaclePlugin;
import com.holub.life.plugin.obstacle.ObstacleType;
import com.holub.life.plugin.toroidal.ToroidalAccessor;
import com.holub.life.plugin.toroidal.ToroidalPlugin;
import com.holub.life.plugin.view2d.View2dPlugin;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import com.holub.life.testkit.FakeSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 3D window itself is shown in the demonstration; these tests cover what is drawn and how the view is wired. */
class View3dPluginTest {
    /** Stands in for the graphics window and records what it was asked to show. */
    private static final class RecordingRenderer implements Renderer3d {
        final List<Scene> shown = new ArrayList<>();
        boolean started, stopped, failToStart;

        @Override public void start() {
            if (failToStart) throw new IllegalStateException("no 3D graphics on this machine");
            started = true;
        }
        @Override public void show(Scene scene) { shown.add(scene); }
        @Override public void stop() { stopped = true; }

        Scene last() { return shown.get(shown.size() - 1); }
    }

    private static CellState aged(int age) {
        return new CellState(FakeHost.ALIVE, Map.of(AgingRule.AGE, Integer.toString(age)));
    }

    private static Pillar pillarAt(Scene scene, int x, int y) {
        return scene.pillars().stream().filter(placed -> placed.x() == x && placed.y() == y)
                .findFirst().orElseThrow().pillar();
    }

    @Test
    void withoutOtherPluginsLivingCellsArePlainColumnsAndTheRestIsFloor() {
        List<CellStyle> styles = Styles.detect(new FakeHost());

        Scene scene = Board3dView.scene(Boards.parse("#.", ".#"), false, styles);

        assertEquals(List.of(new Scene.Placed(0, 0, Styles.PLAIN_ALIVE), new Scene.Placed(1, 1, Styles.PLAIN_ALIVE)),
                scene.pillars());
    }

    @Test
    void withFoodAndAgingInstalledFoodGlowsSeedsPulseAndOlderCellsAreTaller() {
        FakeHost host = new FakeHost().load(new View2dPlugin()).load(new FoodAgingPlugin());
        BoardState board = Boards.parse(
                Map.of('f', FoodType.STATE, 's', BurstSeedType.STATE, 'y', aged(0), 'm', aged(10), 'o', aged(20)), "fsymo");

        Scene scene = Board3dView.scene(board, false, Styles.detect(host));

        assertEquals(Pillar.Glow.STEADY, pillarAt(scene, 0, 0).glow());
        assertEquals(Pillar.Glow.PULSE, pillarAt(scene, 1, 0).glow());
        Pillar young = pillarAt(scene, 2, 0), middle = pillarAt(scene, 3, 0), old = pillarAt(scene, 4, 0);
        assertTrue(young.height() < middle.height() && middle.height() < old.height(), "age is the column height");
        assertEquals(FoodAgingStyle.YOUNG_HEIGHT, young.height());
        assertEquals(FoodAgingStyle.OLD_HEIGHT, old.height());
        assertTrue(young.red() > old.red(), "and the color darkens with age");
        assertEquals(Pillar.Glow.NONE, old.glow());
    }

    @Test
    void sameAgedCellIsDrawnPlainWhenFoodAndAgingIsNotInstalled() {
        Scene scene = Board3dView.scene(Boards.parse(Map.of('o', aged(20)), "o"), false, Styles.detect(new FakeHost()));

        assertEquals(Styles.PLAIN_ALIVE, pillarAt(scene, 0, 0), "the view adapts to the installed plugins");
    }

    @Test
    void withObstacleInstalledWallsAreDrawnAsBlocks() {
        FakeHost host = new FakeHost().load(new View2dPlugin()).load(new ObstaclePlugin());

        Scene scene = Board3dView.scene(Boards.parse(Map.of('w', ObstacleType.STATE), "w#"), false, Styles.detect(host));

        assertEquals(ObstacleStyle.WALL, pillarAt(scene, 0, 0));
        assertEquals(Styles.PLAIN_ALIVE, pillarAt(scene, 1, 0));
    }

    @Test
    void viewShowsTheCurrentBoardThenEveryCommittedBoardAndStopsOnDetach() {
        FakeHost host = new FakeHost();
        FakeSession session = new FakeSession(host, Boards.parse("#."));
        RecordingRenderer renderer = new RecordingRenderer();
        Board3dView view = new Board3dView(() -> renderer);

        view.attach(session, session);
        assertTrue(renderer.started);
        assertEquals(1, renderer.last().pillars().size(), "board at attach time");
        assertEquals("generation 0", renderer.last().caption());

        session.step();
        assertEquals("generation 1", renderer.last().caption(), "followed the commit");

        view.detach();
        assertTrue(renderer.stopped);
        assertEquals(0, session.listenerCount(), "detach removes the listener it registered");
        int before = renderer.shown.size();
        session.step();
        assertEquals(before, renderer.shown.size(), "nothing is shown after detach");
    }

    @Test
    void boardIsShownAsATorusOnlyWhenThePlanUsesTheWrapAroundAccessor() {
        FakeHost host = new FakeHost().load(new ToroidalPlugin());
        RecordingRenderer flat = new RecordingRenderer(), wrapped = new RecordingRenderer();

        FakeSession bounded = new FakeSession(host, Boards.parse("#."));
        new Board3dView(() -> flat).attach(bounded, bounded);
        FakeSession toroidal = new FakeSession(host, Boards.parse("#."), ToroidalAccessor.ID);
        new Board3dView(() -> wrapped).attach(toroidal, toroidal);

        assertFalse(flat.last().torus());
        assertTrue(wrapped.last().torus());
        assertEquals("generation 0  |  torus", wrapped.last().caption());
    }

    @Test
    void viewIsWatchOnly_theBoardIsNeverEdited() {
        FakeSession session = new FakeSession(new FakeHost(), Boards.parse("#.", ".#"));
        BoardState before = session.snapshot();

        Board3dView view = new Board3dView(RecordingRenderer::new);
        view.attach(session, session);
        view.detach();

        assertEquals(before, session.snapshot());
    }

    @Test
    void whenGraphicsCannotStartTheViewStaysOffAndTheGameGoesOn() {
        FakeSession session = new FakeSession(new FakeHost(), Boards.parse("#."));
        RecordingRenderer broken = new RecordingRenderer();
        broken.failToStart = true;
        Board3dView view = new Board3dView(() -> broken);

        assertDoesNotThrow(() -> view.attach(session, session));

        assertEquals(0, session.listenerCount(), "a view that is off does not listen");
        assertEquals(1, session.step().toCompletableFuture().join().generation(), "the game is not affected");
        assertDoesNotThrow(view::detach);
    }

    @Test
    void pluginRegistersTheViewUnderItsId() {
        FakeHost host = new FakeHost().load(new View3dPlugin());

        assertTrue(host.view(Board3dView.ID).isPresent());
    }

    @Test
    void flatLayoutCentersTheBoardOnTheFloor() {
        BoardLayout layout = new BoardLayout(new BoardSize(4, 2), false);

        BoardLayout.Frame corner = layout.frame(0, 0), opposite = layout.frame(3, 1);

        assertEquals(-1.5f, corner.origin()[0]);
        assertEquals(0f, corner.origin()[1]);
        assertEquals(-0.5f, corner.origin()[2]);
        assertEquals(-corner.origin()[0], opposite.origin()[0], "symmetric around the center");
        assertEquals(1f, corner.up()[1], "columns stand straight up");
    }

    @Test
    void torusLayoutWrapsBothBoardAxesAroundARing() {
        BoardLayout layout = new BoardLayout(new BoardSize(8, 8), true);
        float ring = layout.ringRadius(), tube = layout.tubeRadius();
        assertTrue(ring >= 2 * tube, "the hole in the middle stays open");

        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++) {
                BoardLayout.Frame frame = layout.frame(x, y);
                double fromAxis = Math.hypot(frame.origin()[0], frame.origin()[2]);
                double fromTubeCenter = Math.hypot(fromAxis - ring, frame.origin()[1]);
                assertEquals(tube, fromTubeCenter, 1e-4, "every cell sits on the tube surface");
                assertEquals(1.0, length(frame.up()), 1e-5);
                assertEquals(0.0, dot(frame.up(), frame.alongX()), 1e-5, "column directions are perpendicular");
                assertEquals(0.0, dot(frame.up(), frame.alongY()), 1e-5);
            }

        // Column 0 and column 4 of 8 are on opposite sides of the ring.
        BoardLayout.Frame near = layout.frame(0, 0), far = layout.frame(4, 0);
        assertEquals(-near.origin()[0], far.origin()[0], 1e-4);
        assertEquals(-near.origin()[2], far.origin()[2], 1e-4);
        // Rows 0 and 7 are neighbors across the board edge, so they are close together on the tube.
        assertTrue(distance(layout.frame(0, 0).origin(), layout.frame(0, 7).origin())
                < distance(layout.frame(0, 0).origin(), layout.frame(0, 4).origin()));
        assertTrue(layout.frame(0, 0).sizeX() > layout.frame(0, 4).sizeX(), "cells are wider on the outside of the ring");
    }

    private static double length(float[] v) { return Math.sqrt(dot(v, v)); }
    private static double dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double distance(float[] a, float[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }
}
