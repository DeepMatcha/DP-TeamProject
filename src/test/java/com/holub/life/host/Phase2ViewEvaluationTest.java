package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.CoreCellTypes;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.ModelPort;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import com.holub.life.api.ViewPlugin;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class Phase2ViewEvaluationTest {
    private static final BoardSize BOARD_SIZE = new BoardSize(7, 5);
    private static final GridPos TARGET = new GridPos(4, 1);
    private static final String VIEW_2D = "view2d:board";
    private static final String WALL_TOOL = "obstacle:toggle-wall";
    private static final String CORE_TOOL = "core:toggle";

    @Test
    void realSupplementaryViewCommitsAndPaintsAnEnabledWallToolClick() throws Exception {
        requireDisplay();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, providers("view2d", "obstacle"))) {
            enable(host, "view2d", "obstacle");
            try (var view = openSupplementaryView(host)) {
                var wall = host.registry().resolve("obstacle:wall").orElseThrow();
                int before = readEdt(() -> view.pixelAt(TARGET));

                onEdt(() -> view.click(WALL_TOOL, TARGET));
                waitForClick(host, view, wall);

                assertSame(wall, host.model().snapshot().cellAt(TARGET).type());
                assertNotEquals(before, readEdt(() -> view.pixelAt(TARGET)),
                        "The displayed cell must follow the real model commit");
            }
        }
    }

    @Test
    void advertisedCoreToolInTheSupplementaryViewHandlesACellClick() throws Exception {
        requireDisplay();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, providers("view2d"))) {
            enable(host, "view2d");
            try (var view = openSupplementaryView(host)) {
                onEdt(() -> view.click(CORE_TOOL, TARGET));
                var alive = CoreCellTypes.alive(host.registry());
                waitForClick(host, view, alive);

                assertSame(alive, host.model().snapshot().cellAt(TARGET).type(),
                        "Selecting the core toggle from the view's tool list must toggle the target");
            }
        }
    }

    @Test
    void supplementaryViewRespectsTheHostDecisionForADisabledWallTool() throws Exception {
        requireDisplay();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, providers("view2d", "obstacle"))) {
            enable(host, "view2d");
            var input = new ToolInput(TARGET, "click", Map.of());
            assertThrows(ExecutionException.class, () -> await(host.applyTool(WALL_TOOL, input)));
            var before = host.model().snapshot().cellAt(TARGET);
            var edited = new CountDownLatch(1);
            try (var view = openSupplementaryView(host)) {
                host.model().addListener(board -> {
                    if (!before.equals(board.cellAt(TARGET))) edited.countDown();
                });
                onEdt(() -> {
                    // Hiding or disabling an unavailable tool also honors the host policy.
                    if (view.tools.isEnabled() && view.panel.isEnabled()) {
                        for (int i = 0; i < view.tools.getItemCount(); i++) {
                            if (WALL_TOOL.equals(view.tools.getItemAt(i))) {
                                view.click(WALL_TOOL, TARGET);
                                break;
                            }
                        }
                    }
                });
                assertFalse(edited.await(2, TimeUnit.SECONDS),
                        "A tool rejected by the host while disabled edited the cell through its 2D view");
            }
        }
    }

    @Test
    void explicitlyDetachedSupplementaryViewClosesAndStopsFollowingCommits() throws Exception {
        requireDisplay();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, providers("view2d"))) {
            enable(host, "view2d");
            try (var view = openSupplementaryView(host)) {
                int before = readEdt(() -> view.pixelAt(TARGET));
                view.close();

                var alive = new CellState(CoreCellTypes.alive(host.registry()), Map.of());
                await(host.apply(new BoardPatch(List.of(new CellEdit(TARGET, alive)))));
                flushEdt();

                assertFalse(readEdt(view.frame::isDisplayable));
                assertEquals(alive, host.model().snapshot().cellAt(TARGET));
                assertEquals(before, readEdt(() -> view.pixelAt(TARGET)),
                        "An explicitly detached view should no longer consume model commits");
            }
        }
    }

    @Test
    void enabledViewReplacementSuppressesTheDefaultAndDisablingItRestoresTheDefault() throws Exception {
        requireDisplay();
        // No shipped provider declares a VIEW takeover. This probe observes host dispatch.
        var replacement = new ViewReplacementProbe();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, List.of(replacement))) {
            List<Window> existing = readEdt(() -> List.of(Window.getWindows()));
            try {
                onEdt(() -> new SwingLifeView(host).show());
                JFrame defaultFrame = readEdt(() -> defaultWindow(existing));
                enable(host, replacement.id());
                boolean attachedDuringReplacement = replacement.attached.await(5, TimeUnit.SECONDS);
                flushEdt();
                boolean defaultVisibleDuringReplacement = readEdt(defaultFrame::isShowing);

                boolean usedVisibleOffSwitch = readEdt(() -> {
                    for (Window window : Window.getWindows()) {
                        if (window instanceof JFrame frame && frame != defaultFrame && !existing.contains(frame)
                                && frame.isShowing() && frame.getJMenuBar() != null) {
                            JMenuItem item = menuItem(frame, "Plugins", replacement.id());
                            if (item instanceof JCheckBoxMenuItem toggle && toggle.isSelected()) {
                                toggle.doClick(0);
                                return true;
                            }
                        }
                    }
                    return false;
                });
                if (!usedVisibleOffSwitch) {
                    enable(host);
                }
                boolean detachedAfterDisable = replacement.detached.await(5, TimeUnit.SECONDS);
                flushEdt();
                boolean defaultVisibleAfterDisable = readEdt(defaultFrame::isShowing);

                assertAll(
                        () -> assertTrue(attachedDuringReplacement, "Enabled replacement receives attach"),
                        () -> assertFalse(defaultVisibleDuringReplacement, "Default window is suppressed"),
                        () -> assertTrue(detachedAfterDisable, "Disabled replacement receives detach"),
                        () -> assertTrue(defaultVisibleAfterDisable, "Default window returns"),
                        () -> assertTrue(usedVisibleOffSwitch, "The replacement has a reachable off switch"));
            } finally {
                onEdt(() -> {
                    for (Window window : Window.getWindows()) {
                        if (!existing.contains(window)) {
                            window.dispose();
                        }
                    }
                });
            }
        }
    }

    @Test
    void pluginSwitchExposesItsActiveToolWithoutRestartingTheGame() throws Exception {
        requireDisplay();
        try (var host = CoreGame.newSession(BOARD_SIZE, 42, providers("view2d", "obstacle"))) {
            await(host.step());
            await(host.setTickIntervalMillis(60_000));
            await(host.setRunning(true));
            var beforeToggle = await(host.session()).board();
            var initialPlayback = await(host.playbackState());
            List<Window> existing = readEdt(() -> List.of(Window.getWindows()));
            onEdt(() -> new SwingLifeView(host).show());
            JFrame frame = readEdt(() -> defaultWindow(existing));
            try {
                onEdt(() -> {
                    var toggle = (JCheckBoxMenuItem) menuItem(frame, "Plugins", "obstacle");
                    assertFalse(toggle.isSelected(), "Installed extensions start off");
                    assertFalse(menu(frame, "Tools").isVisible(), "The default game has no extra active tool");
                    toggle.doClick(0);
                });
                assertEquals(beforeToggle, await(host.session()).board());
                assertEquals(initialPlayback, await(host.playbackState()));

                onEdt(() -> {
                    assertTrue(menu(frame, "Tools").isVisible());
                    menuItem(frame, "Tools", WALL_TOOL).doClick(0);
                    clickDefaultBoard(frame, TARGET);
                });
                var afterWall = await(host.session()).board();
                assertSame(host.registry().resolve("obstacle:wall").orElseThrow(), afterWall.cellAt(TARGET).type());

                onEdt(() -> menuItem(frame, "Plugins", "obstacle").doClick(0));
                assertEquals(afterWall, await(host.session()).board());
                GridPos untouched = new GridPos(1, 3);
                onEdt(() -> {
                    assertFalse(menu(frame, "Tools").isVisible());
                    clickDefaultBoard(frame, untouched);
                });
                assertSame(CoreCellTypes.alive(host.registry()),
                        await(host.session()).board().cellAt(untouched).type(), "Core editing resumes");
                assertEquals(initialPlayback, await(host.playbackState()));
            } finally {
                onEdt(frame::dispose);
            }
        }
    }

    private static final class ViewReplacementProbe implements LifePlugin, ViewPlugin {
        private final CountDownLatch attached = new CountDownLatch(1);
        private final CountDownLatch detached = new CountDownLatch(1);

        @Override
        public String id() {
            return "test:view-replacement";
        }

        @Override
        public void register(PluginRegistrar registrar) {
            registrar.registerView(this);
            registrar.replaceDefault(ApiPoint.VIEW);
        }

        @Override
        public void attach(ModelPort model, ControllerPort controller) {
            attached.countDown();
        }

        @Override
        public void detach() {
            detached.countDown();
        }
    }

    private static final class SupplementaryWindow implements AutoCloseable {
        private final ViewPlugin view;
        private final JFrame frame;
        private final JPanel panel;
        private final JComboBox<?> tools;
        private boolean attached = true;

        SupplementaryWindow(ViewPlugin view, JFrame frame, JPanel panel, JComboBox<?> tools) {
            this.view = view;
            this.frame = frame;
            this.panel = panel;
            this.tools = tools;
        }

        void click(String toolId, GridPos target) {
            tools.setSelectedItem(toolId);
            assertEquals(toolId, tools.getSelectedItem(), "The tool must be selectable in this view");
            Point point = centerPixel(target);
            panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                    0, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        }

        int pixelAt(GridPos target) {
            var image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                panel.paint(graphics);
                Point point = centerPixel(target);
                return image.getRGB(point.x, point.y);
            } finally {
                graphics.dispose();
            }
        }

        private Point centerPixel(GridPos target) {
            int x = (2 * target.x() + 1) * panel.getWidth() / (2 * BOARD_SIZE.width());
            int y = (2 * target.y() + 1) * panel.getHeight() / (2 * BOARD_SIZE.height());
            return new Point(x, y);
        }

        @Override
        public void close() throws Exception {
            if (attached) {
                onEdt(view::detach);
                attached = false;
                flushEdt();
            }
        }
    }

    private static SupplementaryWindow openSupplementaryView(HostController host) throws Exception {
        var view = host.registry().view(VIEW_2D).orElseThrow();
        List<Window> existing = readEdt(() -> List.of(Window.getWindows()));
        onEdt(() -> view.attach(host.model(), host));
        flushEdt();
        try {
            return readEdt(() -> {
                for (Window window : Window.getWindows()) {
                    if (window instanceof JFrame frame && !existing.contains(frame) && frame.isShowing()
                            && frame.getContentPane().getLayout() instanceof BorderLayout layout) {
                        Component center = layout.getLayoutComponent(BorderLayout.CENTER);
                        Component north = layout.getLayoutComponent(BorderLayout.NORTH);
                        if (center instanceof JPanel panel && north instanceof JComboBox<?> tools) {
                            return new SupplementaryWindow(view, frame, panel, tools);
                        }
                    }
                }
                throw new AssertionError("No displayed supplementary board with an input-tool chooser");
            });
        } catch (Exception | AssertionError failure) {
            onEdt(view::detach);
            throw failure;
        }
    }

    private static JFrame defaultWindow(List<Window> existing) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame && !existing.contains(frame)
                    && frame.isShowing() && frame.getJMenuBar() != null
                    && menu(frame, "Grid") != null && menu(frame, "Go") != null) {
                return frame;
            }
        }
        throw new AssertionError("No visible default board frame");
    }

    private static JMenu menu(JFrame frame, String name) {
        for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
            var menu = frame.getJMenuBar().getMenu(i);
            if (menu != null && name.equals(menu.getText())) {
                return menu;
            }
        }
        return null;
    }

    private static JMenuItem menuItem(JFrame frame, String menuName, String itemName) {
        JMenu group = menu(frame, menuName);
        if (group != null) {
            for (int i = 0; i < group.getItemCount(); i++) {
                JMenuItem item = group.getItem(i);
                if (item != null && itemName.equals(item.getText())) {
                    return item;
                }
            }
        }
        return null;
    }

    private static void clickDefaultBoard(JFrame frame, GridPos target) {
        var content = frame.getContentPane();
        Component board = ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        double scale = Math.min((double) board.getWidth() / BOARD_SIZE.width(),
                (double) board.getHeight() / BOARD_SIZE.height());
        int width = (int) (BOARD_SIZE.width() * scale);
        int height = (int) (BOARD_SIZE.height() * scale);
        int x = (board.getWidth() - width) / 2 + (2 * target.x() + 1) * width / (2 * BOARD_SIZE.width());
        int y = (board.getHeight() - height) / 2 + (2 * target.y() + 1) * height / (2 * BOARD_SIZE.height());
        board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static List<LifePlugin> providers(String... ids) {
        return ServiceLoader.load(LifePlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(plugin -> List.of(ids).contains(plugin.id()))
                .toList();
    }

    private static void enable(HostController host, String... ids) throws Exception {
        var saved = await(host.session()).plan();
        var selections = new ArrayList<PluginSelection>();
        saved.plugins().stream()
                .filter(selection -> selection.pluginId().startsWith("core:"))
                .forEach(selections::add);
        for (String id : ids) {
            selections.add(new PluginSelection(id, true));
        }
        await(host.configure(new SimulationPlan(saved.accessorId(), selections, Map.of())));
    }

    private static void waitForClick(HostController host, SupplementaryWindow view, CellType expected)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (host.model().snapshot().cellAt(TARGET).type() == expected) {
                flushEdt();
                return;
            }
            String status = readEdt(() -> {
                var content = view.frame.getContentPane();
                var layout = (BorderLayout) content.getLayout();
                return ((JLabel) layout.getLayoutComponent(BorderLayout.SOUTH)).getText().strip();
            });
            assertTrue(status.isEmpty(), () -> "The selected tool reported: " + status);
            Thread.sleep(20);
        }
        throw new AssertionError("No model commit after the selected tool was clicked");
    }

    private static <T> T await(CompletionStage<T> operation) throws Exception {
        return operation.toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Swing view evaluation needs a display (for example Xvfb)");
    }

    private static void flushEdt() throws Exception {
        onEdt(() -> {});
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static <T> T readEdt(Callable<T> action) throws Exception {
        var result = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(result);
        return result.get(5, TimeUnit.SECONDS);
    }
}
