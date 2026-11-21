package com.holub.life.host;

import com.holub.life.api.BoardSize;
import com.holub.life.api.GridPos;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class SwingLifeViewTest {
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
                SwingUtilities.invokeAndWait(() -> {
                    var layout = (BorderLayout) frame.getContentPane().getLayout();
                    var board = (JPanel) layout.getLayoutComponent(BorderLayout.CENTER);
                    board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_PRESSED,
                            0, 0, 4, 4, 1, false, MouseEvent.BUTTON1));
                });
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
                clickMenu(frame, "Grid", "Clear");
                assertTrue(host.session().toCompletableFuture().get(5, TimeUnit.SECONDS).board().cells().stream()
                        .allMatch(cell -> cell.type() == CoreCells.EMPTY));
            } finally {
                SwingUtilities.invokeAndWait(frame::dispose);
            }
        }
    }

    private static void clickMenu(JFrame frame, String menuName, String itemName) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < frame.getJMenuBar().getMenuCount(); i++) {
                JMenu menu = frame.getJMenuBar().getMenu(i);
                if (menu.getText().equals(menuName)) {
                    for (int j = 0; j < menu.getItemCount(); j++) {
                        JMenuItem item = menu.getItem(j);
                        if (item != null && item.getText().equals(itemName)) {
                            item.doClick(0);
                            return;
                        }
                    }
                }
            }
            fail("Missing menu item: " + menuName + " / " + itemName);
        });
    }
}
