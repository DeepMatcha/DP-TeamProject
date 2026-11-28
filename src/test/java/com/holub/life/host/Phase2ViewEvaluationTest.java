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
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
                assertTrue(readEdt(() -> hasDefaultWindow(existing)), "The standalone desktop starts visible");
                enable(host, replacement.id());
                flushEdt();
                boolean defaultVisibleDuringReplacement = readEdt(() -> hasDefaultWindow(existing));
                int attachedDuringReplacement = replacement.attached.get();

                enable(host);
                flushEdt();
                boolean defaultVisibleAfterDisable = readEdt(() -> hasDefaultWindow(existing));

                assertAll(
                        () -> assertTrue(attachedDuringReplacement > 0, "Enabled replacement receives attach"),
                        () -> assertFalse(defaultVisibleDuringReplacement, "Default window is suppressed"),
                        () -> assertTrue(replacement.detached.get() > 0, "Disabled replacement receives detach"),
                        () -> assertTrue(defaultVisibleAfterDisable, "Default window returns"));
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

    private static final class ViewReplacementProbe implements LifePlugin, ViewPlugin {
        private final AtomicInteger attached = new AtomicInteger();
        private final AtomicInteger detached = new AtomicInteger();

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
            attached.incrementAndGet();
        }

        @Override
        public void detach() {
            detached.incrementAndGet();
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

    private static boolean hasDefaultWindow(List<Window> existing) {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame && !existing.contains(frame)
                    && frame.isShowing() && frame.getJMenuBar() != null) {
                return true;
            }
        }
        return false;
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
