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
        var registry = new HostRegistry(List.of(
                plugin(BOARD, registrar -> {
                    registrar.registerCellType(CoreCells.EMPTY);
                    registrar.registerCellType(CoreCells.ALIVE);
                    registrar.registerAccessor(new BoundedAccessor());
                    registrar.registerInputTool(new ToggleTool());
                    registrar.registerPatternCodec(new LegacyLifeCodec());
                }),
                plugin(CONWAY, registrar -> registrar.registerLifecycle(new CoreConwayRule()))
        ));
        var plan = new SimulationPlan(BoundedAccessor.ID,
                List.of(new PluginSelection(BOARD, true), new PluginSelection(CONWAY, true)), Map.of());
        var board = new BoardState(size, 0L, seed, Collections.nCopies(size.area(), EMPTY));
        return new HostController(registry, board, plan);
    }

    private static LifePlugin plugin(String id, Consumer<PluginRegistrar> registration) {
        return new LifePlugin() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void register(PluginRegistrar registrar) {
                registration.accept(registrar);
            }
        };
    }

    private static final class ToggleTool implements InputTool {
        @Override
        public String id() {
            return TOGGLE;
        }

        @Override
        public BoardPatch apply(ToolInput input, BoardState current, Accessor<GridPos> accessor) {
            if (!input.action().equals("toggle")) {
                throw new IllegalArgumentException("Unsupported tool action: " + input.action());
            }
            var cell = accessor.access(input.anchor())
                    .orElseThrow(() -> new IllegalArgumentException("Click outside the board"));
            CellState replacement = cell.state().type().isAlive(cell.state()) ? EMPTY : ALIVE;
            return new BoardPatch(List.of(new CellEdit(cell.canonicalPos(), replacement)));
        }
    }
}
