package com.holub.life.plugin.view2d;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.ToolInput;
import com.holub.life.api.ViewPlugin;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Board window that shows every registered type in its plugin's colors.
 * Clicking a cell sends the selected input tool's patch through the Controller;
 * stepping and playback stay with the host's own controls.
 */
public final class Board2dView implements ViewPlugin {
    public static final String ID = "view2d:board";
    private static final String NO_TOOL = "(도구 없음)";

    private final BoardPanel panel = new BoardPanel();
    private final JComboBox<String> toolChoice = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final ModelListener modelListener = board -> SwingUtilities.invokeLater(() -> panel.setBoard(board));
    private ModelPort model;
    private ControllerPort controller;
    private JFrame frame;

    public Board2dView() {
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                String toolId = (String) toolChoice.getSelectedItem();
                if (toolId == null || toolId.equals(NO_TOOL)) return;
                panel.cellAt(event.getX(), event.getY()).ifPresent(cell ->
                        useTool(toolId, cell).whenComplete((board, error) -> SwingUtilities.invokeLater(() ->
                                status.setText(error == null ? " " : "편집 실패: " + error.getMessage()))));
            }
        });
    }

    @Override public String id() { return ID; }

    public BoardPanel panel() { return panel; }

    @Override public void attach(ModelPort model, ControllerPort controller) {
        this.model = model;
        this.controller = controller;
        model.addListener(modelListener);
        BoardState first = model.snapshot();
        SwingUtilities.invokeLater(() -> {
            panel.setBoard(first);
            toolChoice.removeAllItems();
            toolChoice.addItem(NO_TOOL);
            controller.registry().registrations().stream()
                    .filter(registration -> registration.point() == ApiPoint.INPUT_TOOL)
                    .forEach(registration -> toolChoice.addItem(registration.id()));
            if (!GraphicsEnvironment.isHeadless()) openWindow();
        });
    }

    private void openWindow() {
        frame = new JFrame("Life — 플러그인 셀 보기");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.add(toolChoice, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
        frame.setVisible(true);
    }

    /** Asks the tool for a patch against the current session and applies it atomically. */
    public CompletionStage<BoardState> useTool(String toolId, GridPos cell) {
        InputTool tool = controller.registry().inputTool(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown input tool: " + toolId));
        return controller.session().thenCompose(session -> {
            var accessor = controller.registry().accessor(session.plan().accessorId())
                    .orElseThrow(() -> new IllegalStateException("Unknown accessor: " + session.plan().accessorId()))
                    .bind(session.board());
            BoardPatch patch = tool.apply(new ToolInput(cell, "click", Map.of()), session.board(), accessor);
            return controller.apply(patch);
        });
    }

    @Override public void detach() {
        if (model != null) model.removeListener(modelListener);
        model = null;
        controller = null;
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.dispose();
            frame = null;
        });
    }
}
