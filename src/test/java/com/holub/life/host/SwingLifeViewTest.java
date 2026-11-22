package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.CellEdit;
import com.holub.life.api.GridPos;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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

        try (var host = CoreGame.newSession(new BoardSize(64, 64), 1L)) {
            JFrame frame = showView(host);
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
    void swingStoreAndLoadMenusStopPlaybackAndRoundTripAPattern() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Swing test needs a display (for example Xvfb)");

        try (var host = CoreGame.newSession(new BoardSize(64, 64), 1L)) {
            JFrame frame = showView(host);
            try {
                clickCornerCell(frame);
                host.session().toCompletableFuture().get(5, TimeUnit.SECONDS);
                Path saved = testDirectory.resolve("saved.life");
                var block = Set.of(new GridPos(0, 0), new GridPos(1, 0),
                        new GridPos(0, 1), new GridPos(1, 1));
                for (GridPos cell : block) {
                    if (!cell.equals(new GridPos(0, 0))) {
                        clickBoard(frame, 4 + 8 * cell.x(), 4 + 8 * cell.y());
                    }
                }
                startBeforeFileAction(frame, host);
                chooseFile(frame, "Store", saved);
                waitFor("Store did not complete", () -> Files.exists(saved) && isInteractive(frame));
                assertFalse(host.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS).running(),
                        "Successful Store stops playback");
                var stored = LegacyPatternFiles.load(saved, host.registry());
                assertEquals(block, stored.cells().stream().map(CellEdit::position).collect(Collectors.toSet()));

                clickMenu(frame, "Grid", "Clear");
                assertSame(CoreCells.EMPTY, host.session().toCompletableFuture().get(5, TimeUnit.SECONDS)
                        .board().cellAt(new GridPos(0, 0)).type());
                startBeforeFileAction(frame, host);
                chooseFile(frame, "Load", saved);
                waitFor("Load did not restore the stable block", () -> isInteractive(frame)
                        && host.model().snapshot().cellAt(new GridPos(0, 0)).type() == CoreCells.ALIVE);
                var loaded = host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board();
                assertEquals(4, loaded.cells().stream().filter(cell -> cell.type() == CoreCells.ALIVE).count());
                for (GridPos cell : block) {
                    assertSame(CoreCells.ALIVE, loaded.cellAt(cell).type(), cell.toString());
                }
                assertFalse(host.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS).running(),
                        "Successful Load stops playback");
            } finally {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    @Test
    void swingToggleUsesGridCoordinatesAndRepaintsTheCommittedCell() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Swing test needs a display (for example Xvfb)");

        try (var host = CoreGame.newSession(new BoardSize(4, 2), 1L)) {
            JFrame frame = showView(host);
            try {
                var position = new GridPos(3, 1);
                assertCellAppearance(frame, false);
                clickWideBoardCell(frame);
                var alive = host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board();
                assertSame(CoreCells.ALIVE, alive.cellAt(position).type());
                assertEquals(1, alive.cells().stream().filter(cell -> cell.type() == CoreCells.ALIVE).count());
                assertCellAppearance(frame, true);

                clickWideBoardCell(frame);
                var cleared = host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board();
                assertTrue(cleared.cells().stream().allMatch(cell -> cell.type() == CoreCells.EMPTY));
                assertCellAppearance(frame, false);
                assertEquals(0, cleared.generation(), "Both edits happen without a tick");
            } finally {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    private static JFrame showView(HostController host) throws Exception {
        var shown = new AtomicReference<JFrame>();
        SwingUtilities.invokeAndWait(() -> {
            new SwingLifeView(host).show();
            shown.set(Arrays.stream(JFrame.getFrames())
                    .filter(frame -> frame instanceof JFrame && frame.isShowing())
                    .map(JFrame.class::cast).findFirst().orElseThrow());
        });
        return shown.get();
    }

    private static void clickWideBoardCell(JFrame frame) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel board = boardPanel(frame);
            Point point = cellInterior(board, 3, 1);
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED,
                    0, 0, point.x, point.y, 1, false, MouseEvent.BUTTON1));
        });
    }

    private static Point cellInterior(JPanel board, int column, int row) {
        // The test's four columns and two rows fit within the aspect-preserving panel.
        double side = Math.min(board.getWidth() / 4.0, board.getHeight() / 2.0);
        return new Point((int) (board.getWidth() / 2.0 + (column - 1.5) * side),
                (int) (board.getHeight() / 2.0 + (row - 0.5) * side));
    }

    private static void assertCellAppearance(JFrame frame, boolean alive) throws Exception {
        var different = new AtomicBoolean();
        // Flush queued Model notifications, then paint the real panel on the EDT.
        SwingUtilities.invokeAndWait(() -> {
            JPanel board = boardPanel(frame);
            var image = new BufferedImage(board.getWidth(), board.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                board.paint(graphics);
                Point target = cellInterior(board, 3, 1);
                Point reference = cellInterior(board, 0, 0);
                different.set(image.getRGB(target.x, target.y) != image.getRGB(reference.x, reference.y));
            } finally {
                graphics.dispose();
            }
        });
        assertEquals(alive, different.get(), "The toggled cell's appearance must follow the Model");
    }

    private static void startBeforeFileAction(JFrame frame, HostController host) throws Exception {
        // The stable block remains valid even when a tick occurs in the chooser.
        clickMenu(frame, "Go", "Slow");
        assertTrue(host.playbackState().toCompletableFuture().get(5, TimeUnit.SECONDS).running());
    }

    private static void clickCornerCell(JFrame frame) throws Exception {
        clickBoard(frame, 4, 4);
    }

    private static void clickBoard(JFrame frame, int x, int y) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var board = boardPanel(frame);
            board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED,
                    0, 0, x, y, 1, false, MouseEvent.BUTTON1));
        });
    }

    private static JPanel boardPanel(JFrame frame) {
        var layout = (BorderLayout) frame.getContentPane().getLayout();
        return (JPanel) layout.getLayoutComponent(BorderLayout.CENTER);
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
