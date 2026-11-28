package com.holub.life.host;

import com.holub.life.api.GridPos;
import com.holub.life.api.ToolInput;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Tools menu rules without a menu. */
class ToolSelectionTest {
    private static final String WALL = "obstacle:toggle-wall";
    private static final GridPos CELL = new GridPos(2, 1);

    private final ToolSelection selection = new ToolSelection();

    @Test
    void coreToggleIsTheDefaultAndSendsAToggle() {
        assertEquals(Optional.of(new ToolSelection.ToolCommand(CoreGame.TOGGLE,
                new ToolInput(CELL, "toggle", Map.of()))), selection.clickAt(CELL));
    }

    @Test
    void aPluginToolSendsAClick() {
        selection.retainActive(List.of(CoreGame.TOGGLE, WALL));
        selection.select(WALL);

        assertTrue(selection.isSelected(WALL));
        assertEquals(Optional.of(new ToolSelection.ToolCommand(WALL,
                new ToolInput(CELL, "click", Map.of()))), selection.clickAt(CELL));
    }

    @Test
    void aToolWhosePluginIsSwitchedOffFallsBackToTheCoreToggle() {
        selection.select(WALL);

        selection.retainActive(List.of(CoreGame.TOGGLE));

        assertTrue(selection.isSelected(CoreGame.TOGGLE));
    }

    @Test
    void withoutAnyActiveToolAClickSendsNothing() {
        selection.retainActive(List.of());

        assertEquals(Optional.empty(), selection.clickAt(CELL));
    }

    @Test
    void theToolsMenuAppearsOnlyForToolsBeyondTheCoreToggle() {
        assertFalse(ToolSelection.menuVisible(List.of(CoreGame.TOGGLE)));
        assertTrue(ToolSelection.menuVisible(List.of(CoreGame.TOGGLE, WALL)));
        assertEquals("Toggle alive", ToolSelection.label(CoreGame.TOGGLE));
        assertEquals(WALL, ToolSelection.label(WALL));
    }
}
