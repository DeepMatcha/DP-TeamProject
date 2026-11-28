package com.holub.life.host;

import com.holub.life.api.GridPos;
import com.holub.life.api.ToolInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which input tool a click on the default board uses, kept valid as plans change.
 * The core toggle is the default; a tool whose plugin is switched off falls back to it.
 * Holds no Swing state, so the menu rules are testable without a display.
 */
final class ToolSelection {
    private String selected = CoreGame.TOGGLE;

    /** Keeps the selection while it stays active; otherwise the core toggle if active, else no tool. */
    void retainActive(List<String> activeTools) {
        if (selected == null || !activeTools.contains(selected)) {
            selected = activeTools.contains(CoreGame.TOGGLE) ? CoreGame.TOGGLE : null;
        }
    }

    void select(String toolId) {
        selected = toolId;
    }

    boolean isSelected(String toolId) {
        return toolId.equals(selected);
    }

    /** The command a click at the position sends, or empty when no tool is active. */
    Optional<ToolCommand> clickAt(GridPos position) {
        String tool = selected;
        if (tool == null) {
            return Optional.empty();
        }
        String action = CoreGame.TOGGLE.equals(tool) ? "toggle" : "click";
        return Optional.of(new ToolCommand(tool, new ToolInput(position, action, Map.of())));
    }

    static String label(String toolId) {
        return CoreGame.TOGGLE.equals(toolId) ? "Toggle alive" : toolId;
    }

    /** The Tools menu appears only when a plugin adds a tool beyond the core toggle. */
    static boolean menuVisible(List<String> activeTools) {
        return activeTools.stream().anyMatch(id -> !CoreGame.TOGGLE.equals(id));
    }

    record ToolCommand(String toolId, ToolInput input) {}
}
