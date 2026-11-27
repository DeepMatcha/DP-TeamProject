package com.holub.life.plugin.view2d;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.CellType;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.ToolInput;
import com.holub.life.testkit.Boards;
import com.holub.life.testkit.FakeHost;
import com.holub.life.testkit.FakeSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class View2dPluginTest {
    private static final CellType MARKED = new CellType("test:marked") {
        @Override public boolean isAlive(CellState state) { return false; }
    };
    private static final CellState MARKED_STATE = new CellState(MARKED, Map.of("level", "2"));

    /** Test plugin owning one type and one tool that marks the clicked cell. */
    private static final LifePlugin MARKER = new LifePlugin() {
        @Override public String id() { return "test:marker"; }
        @Override public void register(PluginRegistrar registrar) {
            registrar.registerCellType(MARKED);
            registrar.registerInputTool(new InputTool() {
                @Override public String id() { return "test:mark"; }
                @Override public BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor) {
                    return new BoardPatch(List.of(new CellEdit(input.anchor(), MARKED_STATE)));
                }
            });
        }
    };

    @AfterEach
    void forgetPainter() { CellPainters.unregister(MARKED); }

    @Test
    void typeWithoutPainterIsDrawnFromAliveness() {
        assertEquals(CellPainters.DEFAULT_ALIVE, CellPainters.colorOf(FakeHost.ALIVE_STATE));
        assertEquals(CellPainters.DEFAULT_DEAD, CellPainters.colorOf(FakeHost.EMPTY_STATE));
        assertEquals(CellPainters.DEFAULT_DEAD, CellPainters.colorOf(MARKED_STATE));
    }

    @Test
    void registeredPainterDecidesColorFromTheCellProperties() {
        CellPainters.register(MARKED, state -> new Color(0, 0, 100 * Integer.parseInt(state.properties().get("level"))));

        assertEquals(new Color(0, 0, 200), CellPainters.colorOf(MARKED_STATE));
    }

    @Test
    void panelPaintsEachCellInItsPainterColor() {
        CellPainters.register(MARKED, state -> Color.BLUE);
        BoardPanel panel = new BoardPanel();
        panel.setSize(40, 20);
        panel.setBoard(Boards.parse(Map.of('m', MARKED_STATE), "#m"));

        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        panel.paint(image.getGraphics());

        assertEquals(CellPainters.DEFAULT_ALIVE.getRGB(), image.getRGB(10, 10));
        assertEquals(Color.BLUE.getRGB(), image.getRGB(30, 10));
        assertEquals(new GridPos(1, 0), panel.cellAt(30, 10).orElseThrow());
    }

    @Test
    void viewFollowsCommittedBoardsAndStopsAfterDetach() throws Exception {
        FakeHost host = new FakeHost().load(new View2dPlugin());
        FakeSession session = new FakeSession(host, Boards.parse(".."));
        Board2dView view = (Board2dView) host.view(Board2dView.ID).orElseThrow();
        BufferedImage image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        view.panel().setSize(40, 20);

        view.attach(session, session);
        session.apply(new BoardPatch(List.of(new CellEdit(new GridPos(0, 0), FakeHost.ALIVE_STATE))));
        SwingUtilities.invokeAndWait(() -> view.panel().paint(image.getGraphics()));
        assertEquals(CellPainters.DEFAULT_ALIVE.getRGB(), image.getRGB(10, 10), "listener repainted the commit");

        view.detach();
        assertEquals(0, session.listenerCount(), "detach removes the listener it registered");
    }

    @Test
    void clickGoesThroughTheSelectedToolAndTheController() {
        FakeHost host = new FakeHost().load(new View2dPlugin()).load(MARKER);
        FakeSession session = new FakeSession(host, Boards.parse("..", ".."));
        Board2dView view = (Board2dView) host.view(Board2dView.ID).orElseThrow();
        view.attach(session, session);

        BoardState after = view.useTool("test:mark", new GridPos(1, 1)).toCompletableFuture().join();

        assertEquals(MARKED_STATE, Boards.at(after, 1, 1));
        assertEquals(after, session.snapshot(), "the edit was committed to the Model, not drawn locally");
        assertEquals(1, after.cells().stream().filter(MARKED_STATE::equals).count());
    }
}
