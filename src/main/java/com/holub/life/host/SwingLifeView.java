package com.holub.life.host;

import com.holub.life.api.BoardState;
import com.holub.life.api.GridPos;
import com.holub.life.api.HostErrorListener;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ToolInput;
import com.holub.ui.Colors;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Swing adapter: sends commands to the host and paints only committed snapshots. */
public final class SwingLifeView {
    private final HostController host;
    private final JFrame frame = new JFrame("The Game of Life — Allen I. Holub");
    private final JFileChooser chooser = new JFileChooser();
    private final JLabel generation = new JLabel();
    private final BoardPanel board;
    private final ModelListener boardChanged;
    private final HostErrorListener hostError;
    private String fileStatus;

    public SwingLifeView(HostController host) {
        this.host = host;
        board = new BoardPanel(host.model().snapshot());
        boardChanged = state -> SwingUtilities.invokeLater(() -> display(state));
        hostError = error -> showFailure(error.cause());
    }

    /** Called on the Swing event dispatch thread by the application entry point. */
    public void show() {
        chooser.setFileFilter(new FileNameExtensionFilter("Life patterns (*.life)", "life"));
        board.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                board.positionAt(event.getPoint()).ifPresent(position ->
                    report(host.applyTool(CoreGame.TOGGLE, new ToolInput(position, "toggle", Map.of()))));
            }
        });
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setJMenuBar(createMenu());
        frame.add(board, BorderLayout.CENTER);
        frame.add(generation, BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                host.model().removeListener(boardChanged);
                host.removeErrorListener(hostError);
                host.close();
            }
        });
        host.model().addListener(boardChanged);
        host.addErrorListener(hostError);
        display(host.model().snapshot());
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    private JMenuBar createMenu() {
        var menu = new JMenuBar();
        var grid = new JMenu("Grid");
        grid.add(item("Clear", () -> report(host.clear(CoreGame.EMPTY))));
        grid.add(item("Load", this::load));
        grid.add(item("Store", this::store));
        grid.add(item("Exit", frame::dispose));
        menu.add(grid);

        var go = new JMenu("Go");
        go.add(item("Halt", () -> report(host.setRunning(false))));
        go.add(item("Tick (Single Step)", () -> report(host.step())));
        go.add(item("Agonizing", () -> report(host.playAtInterval(500))));
        go.add(item("Slow", () -> report(host.playAtInterval(150))));
        go.add(item("Medium", () -> report(host.playAtInterval(70))));
        go.add(item("Fast", () -> report(host.playAtInterval(30))));
        menu.add(go);
        return menu;
    }

    private static JMenuItem item(String name, Runnable action) {
        var item = new JMenuItem(name);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void display(BoardState state) {
        board.display(state);
        generation.setText(fileStatus == null ? "Generation: " + state.generation() : fileStatus);
    }

    private void load() {
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        var loading = CompletableFuture.supplyAsync(() -> {
            try {
                return LegacyPatternFiles.load(source, host.registry());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        });
        showFileProgress("Loading pattern…", loading.thenCompose(pattern -> host.replacePattern(pattern, CoreGame.EMPTY)));
    }

    private void store() {
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destination = patternDestination(chooser.getSelectedFile().toPath());
        if (Files.exists(destination) && JOptionPane.showConfirmDialog(
                frame, "Replace the existing pattern?", "Store Life pattern",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        showFileProgress("Storing pattern…", host.setRunning(false).thenCompose(ignored -> host.session()).thenAcceptAsync(snapshot -> {
            try {
                LegacyPatternFiles.store(destination, snapshot.board(), host.registry());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }));
    }

    private void showFileProgress(String status, CompletionStage<?> command) {
        // Keep user commands in order while the worker reads or replaces a file.
        fileStatus = status;
        generation.setText(status);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setEnabled(false);
        command.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            frame.setEnabled(true);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            fileStatus = null;
            display(host.model().snapshot());
            if (error != null) {
                showFailure(error);
            }
        }));
    }

    private static Path patternDestination(Path path) {
        String name = path.getFileName().toString();
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".life")
                ? path : path.resolveSibling(name + ".life");
    }

    private void report(CompletionStage<?> command) {
        command.whenComplete((result, error) -> {
            if (error != null) {
                showFailure(error);
            }
        });
    }

    private void showFailure(Throwable error) {
        SwingUtilities.invokeLater(() -> {
            if (frame.isDisplayable()) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                JOptionPane.showMessageDialog(frame, cause.toString(), "Life operation failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static final class BoardPanel extends JPanel {
        private BoardState state;

        BoardPanel(BoardState initial) {
            state = initial;
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(512, 512));
        }

        void display(BoardState next) {
            state = next;
            repaint();
        }

        Optional<GridPos> positionAt(Point point) {
            Rectangle area = gridBounds();
            if (!area.contains(point)) {
                return Optional.empty();
            }
            // Invert the same rounded-down boundaries used for painting.
            int x = (int) (((point.x - area.x + 1L) * state.size().width() - 1) / area.width);
            int y = (int) (((point.y - area.y + 1L) * state.size().height() - 1) / area.height);
            return Optional.of(new GridPos(x, y));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Rectangle area = gridBounds();
            int width = state.size().width();
            int height = state.size().height();
            for (int y = 0; y < height; y++) {
                int top = area.y + scaled(y, area.height, height);
                int bottom = area.y + scaled(y + 1, area.height, height);
                for (int x = 0; x < width; x++) {
                    int left = area.x + scaled(x, area.width, width);
                    int right = area.x + scaled(x + 1, area.width, width);
                    var cell = state.cells().get(y * width + x);
                    graphics.setColor(cell.type().isAlive(cell) ? Color.RED : Colors.LIGHT_YELLOW);
                    graphics.fillRect(left, top, right - left, bottom - top);
                    graphics.setColor(Colors.DARK_YELLOW);
                    graphics.drawLine(left, top, right, top);
                    graphics.drawLine(left, top, left, bottom);
                }
            }
        }

        private static int scaled(int coordinate, int pixels, int total) {
            return (int) ((long) coordinate * pixels / total);
        }

        private Rectangle gridBounds() {
            double scale = Math.min((double) getWidth() / state.size().width(),
                    (double) getHeight() / state.size().height());
            int width = (int) (state.size().width() * scale);
            int height = (int) (state.size().height() * scale);
            return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2, width, height);
        }
    }
}
