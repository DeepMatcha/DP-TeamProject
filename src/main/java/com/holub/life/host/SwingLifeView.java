package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardState;
import com.holub.life.api.HostErrorListener;
import com.holub.life.api.ModelListener;
import com.holub.life.api.PluginInfo;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.api.SimulationPlan;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

/** Swing adapter: sends commands to the host and paints only committed snapshots. */
public final class SwingLifeView {
    private static final Set<String> BUILT_IN_PLUGINS = Set.of("core:board", "core:conway");

    private final HostController host;
    private final Runnable afterHostClosed;
    private final JFrame frame = new JFrame("The Game of Life — Allen I. Holub");
    private final JLabel generation = new JLabel();
    private final JMenu tools = new JMenu("Tools");
    private final BoardPanel board;
    private final DesktopPluginViews pluginViews;
    private final SnapshotMenus snapshotMenus;
    private final PatternMenus patternMenus;
    private final ToolSelection toolSelection = new ToolSelection();
    private final List<PluginInfo> plugins;
    private final Map<String, List<JCheckBoxMenuItem>> pluginItems = new HashMap<>();
    private final Set<String> pendingPlugins = new HashSet<>();
    private final ModelListener boardChanged;
    private final HostErrorListener hostError;
    private final Consumer<SimulationPlan> planChanged;
    private JFrame pluginControls;
    private SimulationPlan displayedPlan;
    private String fileStatus;
    private boolean closed;

    public SwingLifeView(HostController host) {
        this(host, () -> {});
    }

    public SwingLifeView(HostController host, Runnable afterHostClosed) {
        this.host = Objects.requireNonNull(host, "host");
        this.afterHostClosed = Objects.requireNonNull(afterHostClosed, "afterHostClosed");
        board = new BoardPanel(host.model().snapshot());
        pluginViews = new DesktopPluginViews(host, this::showFailure);
        snapshotMenus = new SnapshotMenus(host, frame, this::showFileProgress);
        patternMenus = new PatternMenus(host, frame, this::showFileProgress);
        plugins = host.registry().plugins().stream()
                .filter(plugin -> !BUILT_IN_PLUGINS.contains(plugin.id()))
                .toList();
        boardChanged = state -> SwingUtilities.invokeLater(() -> display(state));
        hostError = error -> showFailure(error.cause());
        planChanged = ignored -> SwingUtilities.invokeLater(() -> applyPlan(host.currentPlan()));
    }

    /** Called on the Swing event dispatch thread by the application entry point. */
    public void show() {
        board.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                board.positionAt(event.getPoint()).flatMap(toolSelection::clickAt).ifPresent(command ->
                        report(host.applyTool(command.toolId(), command.input())));
            }
        });
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setJMenuBar(createMenu());
        frame.add(board, BorderLayout.CENTER);
        frame.add(generation, BorderLayout.SOUTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeSession();
            }
        });
        host.model().addListener(boardChanged);
        host.addErrorListener(hostError);
        host.addPlanListener(planChanged);
        display(host.model().snapshot());
        frame.pack();
        frame.setLocationByPlatform(true);
        applyPlan(host.currentPlan());
    }

    private JMenuBar createMenu() {
        var menu = new JMenuBar();
        var grid = new JMenu("Grid");
        grid.add(item("Clear", () -> report(host.clear(CoreGame.EMPTY))));
        patternMenus.install(grid);
        snapshotMenus.install(grid);
        grid.add(item("Exit", this::closeSession));
        menu.add(grid);

        var go = new JMenu("Go");
        go.add(item("Halt", () -> report(host.setRunning(false))));
        go.add(item("Tick (Single Step)", () -> report(host.step())));
        for (SpeedPreset speed : SpeedPreset.values()) {
            go.add(item(speed.label, () -> report(host.playAtInterval(speed.intervalMillis))));
        }
        menu.add(go);
        menu.add(tools);
        if (!plugins.isEmpty()) {
            menu.add(createPluginsMenu());
        }
        return menu;
    }

    private JMenu createPluginsMenu() {
        var menu = new JMenu("Plugins");
        menu.setToolTipText("Enabled rules run in plan order");
        for (PluginInfo plugin : plugins) {
            String id = plugin.id();
            var toggle = new JCheckBoxMenuItem(id);
            toggle.addActionListener(event -> togglePlugin(id, toggle.isSelected()));
            pluginItems.computeIfAbsent(id, ignored -> new ArrayList<>()).add(toggle);
            menu.add(toggle);
        }
        return menu;
    }

    private void togglePlugin(String id, boolean enabled) {
        if (closed || !pendingPlugins.add(id)) {
            return;
        }
        pluginItems.get(id).forEach(toggle -> toggle.setEnabled(false));
        host.setPluginEnabled(id, enabled).whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            pendingPlugins.remove(id);
            if (!closed) {
                applyPlan(host.currentPlan());
                syncPluginItems(host.currentPlan());
                if (error != null) {
                    showFailure(error);
                }
            }
        }));
    }

    private void syncPluginItems(SimulationPlan plan) {
        var active = plan.plugins().stream().filter(PluginSelection::enabled)
                .map(PluginSelection::pluginId).collect(Collectors.toSet());
        pluginItems.forEach((id, toggles) -> toggles.forEach(toggle -> {
            toggle.setSelected(active.contains(id));
            toggle.setEnabled(!pendingPlugins.contains(id));
        }));
    }

    private void applyPlan(SimulationPlan plan) {
        if (closed || plan.equals(displayedPlan)) {
            return;
        }
        displayedPlan = plan;
        if (host.registry().replacesDefault(plan, ApiPoint.VIEW)) {
            showPluginControls();
            if (frame.isVisible()) {
                frame.setVisible(false);
            }
        } else {
            if (!frame.isVisible()) {
                frame.setVisible(true);
            }
            if (pluginControls != null) {
                pluginControls.setVisible(false);
            }
        }
        syncPluginItems(plan);
        updateTools(plan);
        snapshotMenus.planChanged(plan);
        pluginViews.update(plan);
    }

    private void updateTools(SimulationPlan plan) {
        List<String> active = host.registry().activeRegistrations(plan, ApiPoint.INPUT_TOOL).stream()
                .map(RegistrationInfo::id).toList();
        toolSelection.retainActive(active);
        tools.removeAll();
        var group = new ButtonGroup();
        for (String id : active) {
            var item = new JRadioButtonMenuItem(ToolSelection.label(id));
            item.setSelected(toolSelection.isSelected(id));
            item.addActionListener(event -> toolSelection.select(id));
            group.add(item);
            tools.add(item);
        }
        tools.setVisible(ToolSelection.menuVisible(active));
    }

    /** A takeover can hide the default window, so its off switch needs a separate home. */
    private void showPluginControls() {
        if (pluginControls == null) {
            pluginControls = new JFrame("Life plug-ins");
            pluginControls.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            pluginControls.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    closeSession();
                }
            });
            var menu = new JMenuBar();
            menu.add(createPluginsMenu());
            var session = new JMenu("Session");
            session.add(item("Exit", this::closeSession));
            menu.add(session);
            pluginControls.setJMenuBar(menu);
            pluginControls.add(new JLabel("Use Plugins to return to the default view."));
            pluginControls.pack();
            pluginControls.setLocationByPlatform(true);
        }
        if (!pluginControls.isVisible()) {
            pluginControls.setVisible(true);
        }
    }

    private void closeSession() {
        if (closed) {
            return;
        }
        closed = true;
        host.removePlanListener(planChanged);
        host.model().removeListener(boardChanged);
        host.removeErrorListener(hostError);
        host.setRunning(false);
        frame.dispose();
        if (pluginControls != null) {
            pluginControls.dispose();
        }
        pluginViews.close(() -> SwingUtilities.invokeLater(() -> {
            try {
                host.close();
            } finally {
                afterHostClosed.run();
            }
        }));
    }

    private static JMenuItem item(String name, Runnable action) {
        var item = new JMenuItem(name);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void display(BoardState state) {
        if (closed) {
            return;
        }
        board.display(state);
        generation.setText(fileStatus == null ? "Generation: " + state.generation() : fileStatus);
    }

    private void showFileProgress(String status, CompletionStage<?> command) {
        // Keep user commands in order while the worker reads or replaces a file.
        fileStatus = status;
        generation.setText(status);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setEnabled(false);
        command.whenComplete((result, error) -> SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            frame.setEnabled(true);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            fileStatus = null;
            display(host.model().snapshot());
            if (error != null) {
                showFailure(error);
            }
        }));
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
            JFrame owner = frame.isShowing() ? frame : pluginControls;
            if (!closed && owner != null && owner.isDisplayable()) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null
                        ? error.getCause() : error;
                JOptionPane.showMessageDialog(owner, cause.toString(), "Life operation failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
