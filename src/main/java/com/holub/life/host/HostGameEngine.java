package com.holub.life.host;

import com.holub.life.api.Accessor;
import com.holub.life.api.AccessorFactory;
import com.holub.life.api.ApiPoint;
import com.holub.life.api.BoardSize;
import com.holub.life.api.BoardState;
import com.holub.life.api.CellState;
import com.holub.life.api.GameEngine;
import com.holub.life.api.GridPos;
import com.holub.life.api.LifecycleManager;
import com.holub.life.api.PluginInfo;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.SimulationPlan;
import com.holub.life.api.StepContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/** Runs each enabled lifecycle rule against an immutable whole-board stage input. */
public final class HostGameEngine implements GameEngine {
    private final HostRegistry registry;
    private final Set<String> pluginIds;
    private final String defaultLifecyclePluginId;

    public HostGameEngine(HostRegistry registry) {
        this(registry, null);
    }

    HostGameEngine(HostRegistry registry, String defaultLifecyclePluginId) {
        this.registry = Objects.requireNonNull(registry, "registry");
        pluginIds = registry.plugins().stream().map(PluginInfo::id)
            .collect(Collectors.toUnmodifiableSet());
        if (defaultLifecyclePluginId != null) {
            requirePlugin(defaultLifecyclePluginId);
        }
        this.defaultLifecyclePluginId = defaultLifecyclePluginId;
    }

    @Override
    public BoardState calcNext(BoardState source, SimulationPlan plan) {
        AccessorFactory<GridPos> factory = validatedAccessor(plan);
        validateBoard(source);
        long nextGeneration = Math.incrementExact(source.generation());
        // One fresh stream per tick, consumed in stage order and then row-major
        // order. The odd multiplier keeps consecutive generations distinct for a seed.
        long tickSeed = source.seed() ^ (source.generation() * 0x9E3779B97F4A7C15L);
        var random = new SplittableRandom(tickSeed);
        BoardState currentStage = source;
        boolean replacesDefault = defaultLifecyclePluginId != null && plan.plugins().stream()
            .anyMatch(plugin -> plugin.enabled() && !plugin.pluginId().equals(defaultLifecyclePluginId)
                && registry.replacesDefault(plugin.pluginId(), ApiPoint.LIFECYCLE));
        for (PluginSelection plugin : plan.plugins()) {
            if (!plugin.enabled() || (replacesDefault && plugin.pluginId().equals(defaultLifecyclePluginId))) {
                continue;
            }
            for (LifecycleManager stage : registry.lifecycleStages(plugin.pluginId())) {
                currentStage = evaluateStage(currentStage, factory, stage, random);
            }
        }
        return new BoardState(source.size(), nextGeneration, source.seed(), currentStage.cells());
    }

    /** Checks that a plan can run on the registered host capabilities. */
    public void validatePlan(SimulationPlan plan) {
        validatedAccessor(plan);
    }

    /** Checks common structure and that all cell types are registered by identity. */
    public void validateBoard(BoardState board) {
        Objects.requireNonNull(board, "board").cells().forEach(this::validateCell);
    }

    /** Checks the cell against the host's registered type singletons. */
    public void validateCell(CellState cell) {
        Objects.requireNonNull(cell, "cell state");
        if (registry.idOf(cell.type()).isEmpty()) {
            throw new IllegalArgumentException("Unregistered cell type: " + cell.type().id());
        }
    }

    private AccessorFactory<GridPos> validatedAccessor(SimulationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        AccessorFactory<GridPos> factory = registry.accessor(plan.accessorId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown accessor: " + plan.accessorId()));
        if (factory.coordinateType() != GridPos.class) {
            throw new IllegalArgumentException("Accessor must use GridPos: " + plan.accessorId());
        }
        for (PluginSelection plugin : plan.plugins()) {
            requirePlugin(plugin.pluginId());
        }
        plan.pluginOptions().forEach((pluginId, options) -> {
            requirePlugin(pluginId);
            if (!options.isEmpty()) {
                throw new IllegalArgumentException("Plugin options are unsupported: " + pluginId);
            }
        });
        return factory;
    }

    private void requirePlugin(String id) {
        if (!pluginIds.contains(id)) {
            throw new IllegalArgumentException("Unknown plugin: " + id);
        }
    }

    private BoardState evaluateStage(
            BoardState input, AccessorFactory<GridPos> factory, LifecycleManager stage, SplittableRandom random) {
        Accessor<GridPos> accessor = Objects.requireNonNull(factory.bind(input), "Bound accessor");
        BoardSize size = input.size();
        List<CellState> next = new ArrayList<>(size.area());
        boolean bounded = factory instanceof BoundedAccessor;
        for (int y = 0; y < size.height(); y++) {
            for (int x = 0; x < size.width(); x++) {
                var window = new SnapshotNeighborBoard(input, accessor, new GridPos(x, y), stage.distance(), bounded);
                CellState result = stage.figureNextState(window, new StepContext(input.generation(), random));
                validateCell(result);
                next.add(result);
            }
        }
        return new BoardState(size, input.generation(), input.seed(), next);
    }
}
