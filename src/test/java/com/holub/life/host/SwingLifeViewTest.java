package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.GridPos;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SwingLifeViewTest {
    @TempDir
    Path testDirectory;

    @Test
    void swingDispatchesEditStepAndPlaybackToTheHost() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Swing test needs a display (for example Xvfb)");

        var shown = new AtomicReference<JFrame>();
        try (var host = CoreGame.newSession(new BoardSize(64, 64), 1L)) {
            SwingUtilities.invokeAndWait(() -> {
                new SwingLifeView(host).show();
                shown.set(Arrays.stream(JFrame.getFrames())
                        .filter(frame -> frame instanceof JFrame && frame.isShowing())
                        .map(JFrame.class::cast).findFirst().orElseThrow());
            });
            JFrame frame = shown.get();
            try {
                clickCornerCell(frame);
                var clicked = host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board();
                assertSame(CoreCells.ALIVE, clicked.cellAt(new GridPos(0, 0)).type());

                clickMenu(frame, "Go", "Tick (Single Step)");
                var stepped = host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board();
                assertEquals(1, stepped.generation());
                assertSame(CoreCells.EMPTY, stepped.cellAt(new GridPos(0, 0)).type());

                var automated = new CountDownLatch(1);
                host.model().addListener(board -> automated.countDown());
                clickMenu(frame, "Go", "Medium");
                var running = host.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS);
                assertTrue(running.running());
                assertEquals(70, running.millisecondsBetweenTicks());
                assertTrue(automated.await(5, TimeUnit.SECONDS));

                clickMenu(frame, "Go", "Halt");
                assertFalse(host.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS).running());
                clickCornerCell(frame);
                assertSame(CoreCells.ALIVE, host.session().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .board().cellAt(new GridPos(0, 0)).type());
                clickMenu(frame, "Grid", "Clear");
                assertTrue(host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board().cells().stream()
                        .allMatch(cell -> cell.type() == CoreCells.EMPTY));
            } finally {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    @Test
    void swingStoreAndLoadMenusRoundTripAPattern() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Swing test needs a display (for example Xvfb)");

        var shown = new AtomicReference<JFrame>();
        try (var host = CoreGame.newSession(new BoardSize(64, 64), 1L)) {
            SwingUtilities.invokeAndWait(() -> {
                new SwingLifeView(host).show();
                shown.set(Arrays.stream(JFrame.getFrames())
                        .filter(frame -> frame instanceof JFrame && frame.isShowing())
                        .map(JFrame.class::cast).findFirst().orElseThrow());
            });
            JFrame frame = shown.get();
            try {
                clickCornerCell(frame);
                host.session().toCompletableFuture().get(5, TimeUnit.SECONDS);
                Path saved = testDirectory.resolve("saved.life");
                chooseFile(frame, "Store", saved);
                waitFor("Store did not complete", () -> Files.exists(saved) && isInteractive(frame));

                clickMenu(frame, "Grid", "Clear");
                assertSame(CoreCells.EMPTY, host.session().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .board().cellAt(new GridPos(0, 0)).type());
                chooseFile(frame, "Load", saved);
                waitFor("Load did not restore the live cell", () -> isInteractive(frame)
                        && host.model().snapshot().cellAt(new GridPos(0, 0)).type() == CoreCells.ALIVE);
            } finally {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    private static void clickCornerCell(JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var layout = (BorderLayout) frame.getContentPane().getLayout();
            var board = (JPanel) layout.getLayoutComponent(BorderLayout.CENTER);
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED,
                    0, 0, 4, 4, 1, false, MouseEvent.BUTTON1));
        });
    }

    private static void chooseFile(JFrame frame, String itemName, Path path) throws Exception {
        SwingUtilities.invokeLater(() -> findItem(frame, "Grid", itemName).doClick(0));
        waitFor("File chooser did not open for " + itemName, () -> {
            var approved = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> {
                for (Window window : frame.getOwnedWindows()) {
                    if (window.isShowing()) {
                        JFileChooser chooser = findChooser(window);
                        if (chooser != null) {
                            chooser.setSelectedFile(path.toFile());
                            chooser.approveSelection();
                            approved.set(true);
                            return;
                        }
                    }
                }
            });
            return approved.get();
        });
    }

    private static JFileChooser findChooser(Component component) {
        if (component instanceof JFileChooser chooser) {
            return chooser;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JFileChooser chooser = findChooser(child);
                if (chooser != null) {
                    return chooser;
                }
            }
        }
        return null;
    }

    private static boolean isInteractive(JFrame frame) throws Exception {
        var enabled = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> enabled.set(frame.isEnabled()));
        return enabled.get();
    }

    private static void waitFor(String failure, CheckedCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        do {
            if (condition.test()) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        fail(failure);
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean test() throws Exception;
    }

    private static void clickMenu(JFrame frame, String menuName, String itemName) throws Exception {
        SwingUtilities.invokeAndWait(() -> findItem(frame, menuName, itemName).doClick(0));
    }

    private static JMenuItem findItem(JFrame frame, String menuName, String itemName) {
        for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
            JMenu menu = frame.getJMenuBar().getMenu(i);
            if (menu.getText().equals(menuName)) {
                for (int j = 0; j < menu.getItemCount(); j++) {
                    JMenuItem item = menu.getItem(j);
                    if (item != null && item.getText().equals(itemName)) {
                        return item;
                    }
                }
            }
        }
        return fail("Missing menu item: " + menuName + " / " + itemName);
    }
}
