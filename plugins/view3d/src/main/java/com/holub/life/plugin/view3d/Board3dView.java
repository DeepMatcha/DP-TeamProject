package com.holub.life.plugin.view3d;

import com.holub.life.api.BoardState;
import com.holub.life.api.ControllerPort;
import com.holub.life.api.GridPos;
import com.holub.life.api.ModelListener;
import com.holub.life.api.ModelPort;
import com.holub.life.api.ViewPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Watch-only 3D window that follows the committed board. It never edits: all input stays with the 2D views.
 * What it draws depends on the installed plugins, and the board becomes a torus when the plan wraps.
 * If 3D graphics cannot start, the view logs why and stays off; the game is not affected.
 */
public final class Board3dView implements ViewPlugin {
    public static final String ID = "view3d:board";
    /** Same value as the toroidal plugin's accessor ID; compared as text so that plugin stays optional. */
    static final String TOROIDAL_ACCESSOR = "toroidal:wrap";
    private static final System.Logger LOG = System.getLogger(Board3dView.class.getName());

    private final Supplier<Renderer3d> rendererFactory;
    private final ModelListener modelListener = board -> refresh();
    private ModelPort model;
    private ControllerPort controller;
    private Renderer3d renderer;
    private List<CellStyle> styles;

    public Board3dView() { this(JmeRenderer::new); }

    /** For tests: a renderer that does not need a graphics window. */
    Board3dView(Supplier<Renderer3d> rendererFactory) { this.rendererFactory = rendererFactory; }

    @Override public String id() { return ID; }

    @Override public void attach(ModelPort model, ControllerPort controller) {
        Renderer3d started;
        try {
            styles = Styles.detect(controller.registry());
            started = rendererFactory.get();
            started.start();
        } catch (RuntimeException | LinkageError failure) {
            LOG.log(System.Logger.Level.WARNING, "3D view is off: 3D graphics could not be started.", failure);
            return;
        }
        this.renderer = started;
        this.model = model;
        this.controller = controller;
        model.addListener(modelListener);
        refresh();
    }

    /** Board and plan are read together from the session, so the layout matches the board that is drawn. */
    private void refresh() {
        Renderer3d target = renderer;
        ControllerPort source = controller;
        List<CellStyle> current = styles;
        if (target == null || source == null) return;
        source.session().thenAccept(session -> target.show(
                scene(session.board(), TOROIDAL_ACCESSOR.equals(session.plan().accessorId()), current)));
    }

    static Scene scene(BoardState board, boolean torus, List<CellStyle> styles) {
        List<Scene.Placed> pillars = new ArrayList<>();
        for (int y = 0; y < board.size().height(); y++)
            for (int x = 0; x < board.size().width(); x++) {
                int column = x, row = y;
                Styles.pillarOf(styles, board.cellAt(new GridPos(x, y)))
                        .ifPresent(pillar -> pillars.add(new Scene.Placed(column, row, pillar)));
            }
        return new Scene(board.size(), torus, pillars,
                "generation " + board.generation() + (torus ? "  |  torus" : ""));
    }

    @Override public void detach() {
        if (model != null) model.removeListener(modelListener);
        if (renderer != null) renderer.stop();
        model = null;
        controller = null;
        renderer = null;
    }
}
