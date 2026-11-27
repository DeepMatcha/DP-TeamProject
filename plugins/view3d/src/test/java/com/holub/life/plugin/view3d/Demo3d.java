package com.holub.life.plugin.view3d;

import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.plugin.foodaging.FoodAgingPlugin;
import com.holub.life.plugin.obstacle.ObstaclePlugin;
import com.holub.life.plugin.obstacle.ObstacleType;
import com.holub.life.plugin.toroidal.ToroidalAccessor;
import com.holub.life.plugin.toroidal.ToroidalPlugin;
import com.holub.life.plugin.view2d.Board2dView;
import com.holub.life.plugin.view2d.View2dPlugin;
import com.holub.life.testkit.FakeHost;
import com.holub.life.testkit.FakeSession;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manual look at the plugins together on the fake host: run with {@code gradlew :plugins:view3d:demo}.
 * Pass {@code flat} for a bounded board. This is a demonstration, not a test, and the real host is not involved.
 * The wall-aware Conway rule supplies the evolution, since the fake host has no Conway stage of its own.
 */
public final class Demo3d {
    public static void main(String[] args) throws Exception {
        boolean torus = args.length == 0 || !args[0].equals("flat");
        FakeHost host = new FakeHost()
                .load(new View2dPlugin()).load(new ObstaclePlugin()).load(new FoodAgingPlugin())
                .load(new ToroidalPlugin()).load(new View3dPlugin());
        FakeSession session = new FakeSession(host, randomBoard(48, 48),
                torus ? ToroidalAccessor.ID : FakeHost.BOUNDED_ACCESSOR);
        host.view(Board2dView.ID).orElseThrow().attach(session, session);
        // The demo owns the renderer so that it can save a picture of the 3D window itself.
        JmeRenderer renderer = new JmeRenderer();
        new Board3dView(() -> renderer).attach(session, session);

        String shot = System.getProperty("demo.shot");
        long end = System.currentTimeMillis() + 1000L * Integer.getInteger("demo.seconds", Integer.MAX_VALUE / 1000);
        while (System.currentTimeMillis() < end) {
            session.step();
            Thread.sleep(150);
        }
        if (shot != null) {
            File file = new File(shot);
            renderer.screenshot(file.getParent(), file.getName().replace(".png", ""));
            Thread.sleep(1500);
        }
        System.exit(0);
    }

    private static BoardState randomBoard(int width, int height) {
        Random random = new Random(2026);
        List<CellState> cells = new ArrayList<>();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                boolean wall = (x == width / 3 || x == 2 * width / 3) && y % 8 < 5;
                cells.add(wall ? ObstacleType.STATE
                        : random.nextInt(100) < 30 ? FakeHost.ALIVE_STATE : FakeHost.EMPTY_STATE);
            }
        return new BoardState(new BoardSize(width, height), 0, 2026, cells);
    }
}
