package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.BoardPatch;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellEdit;
import com.holub.life.api.CellState;
import com.holub.life.api.GridPos;
import com.holub.life.api.InputTool;
import com.holub.life.api.LifePlugin;
import com.holub.life.api.PluginRegistrar;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.ToolInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The standalone host's composition of default types, tools, codec and rule. */
public final class CoreGame {
    static final CellState EMPTY = new CellState(CoreCells.EMPTY, Map.of());
    static final CellState ALIVE = new CellState(CoreCells.ALIVE, Map.of());
    static final String TOGGLE = "core:toggle";
    private static final String BOARD = "core:board";
    private static final String CONWAY = "core:conway";

    private CoreGame() {}

    public static HostController newSession(BoardSize size, long seed) {
        return newSession(size, seed, List.of());
    }

    /** Creates a standalone session with additional modules registered but not enabled. */
    public static HostController newSession(BoardSize size, long seed, List<? extends LifePlugin> plugins) {
        var modules = new ArrayList<LifePlugin>(List.of(
                new BuiltInPlugin(BOARD, registrar -> {
                    registrar.registerAccessor(new BoundedAccessor());
                    registrar.registerInputTool(new ToggleTool());
                    registrar.registerPatternCodec(new LegacyLifeCodec());
                }),
                new BuiltInPlugin(CONWAY, registrar -> registrar.registerLifecycle(new CoreConwayRule()))
        ));
        modules.addAll(List.copyOf(plugins));
        var registry = new HostRegistry(modules);
        var plan = new SimulationPlan(BoundedAccessor.ID,
                List.of(new PluginSelection(BOARD, true), new PluginSelection(CONWAY, true)), Map.of());
        var board = new BoardState(size, 0L, seed, Collections.nCopies(size.area(), EMPTY));
        return new HostController(registry, board, plan, CONWAY);
    }

    /** A host-owned module set, registered through the same API as an installed plugin. */
    private record BuiltInPlugin(String id, Consumer<PluginRegistrar> registration) implements LifePlugin {
        @Override
        public void register(PluginRegistrar registrar) {
            registration.accept(registrar);
        }
    }

    private static final class ToggleTool implements InputTool {
        @Override
        public String id() {
            return TOGGLE;
        }

        @Override
        public BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor) {
            if (!input.action().equals("toggle") && !input.action().equals("click")) {
                throw new IllegalArgumentException("Unsupported tool action: " + input.action());
            }
            var cell = accessor.access(input.anchor())
                    .orElseThrow(() -> new IllegalArgumentException("Click outside the board"));
            CellState replacement = cell.state().type().isAlive(cell.state()) ? EMPTY : ALIVE;
            return new BoardPatch(List.of(new CellEdit(cell.canonicalPos(), replacement)));
        }
    }
}
