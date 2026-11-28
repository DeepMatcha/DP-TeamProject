package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.PluginInfo;
import com.holub.life.api.PluginSelection;
import com.holub.life.api.RegistrationInfo;
import com.holub.life.api.SimulationPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Policy for turning one provider on or off in a plan. It computes the next plan only;
 * the controller validates and commits it at an operation boundary.
 * <ul>
 * <li>A provider keeps its catalog position among the plan's selections.</li>
 * <li>Enabling a provider with accessors selects its first registered accessor unless
 *     one of its accessors is already selected.</li>
 * <li>Removing the active accessor's provider prefers the host's initial accessor, then
 *     the first enabled accessor in plan order.</li>
 * </ul>
 */
final class PluginSwitch {
    private final HostRegistry registry;
    private final String defaultAccessorId;
    private final List<String> catalogOrder;

    PluginSwitch(HostRegistry registry, String defaultAccessorId) {
        this.registry = registry;
        this.defaultAccessorId = defaultAccessorId;
        catalogOrder = registry.plugins().stream().map(PluginInfo::id).toList();
    }

    /** The plan with the provider switched, or empty when the plan already has that state. */
    Optional<SimulationPlan> switched(SimulationPlan plan, String pluginId, boolean enabled) {
        if (!catalogOrder.contains(pluginId)) {
            throw new IllegalArgumentException("Unknown plugin: " + pluginId);
        }
        var accessors = registry.registrations().stream()
                .filter(registration -> registration.point() == ApiPoint.ACCESSOR
                        && registration.pluginId().equals(pluginId))
                .map(RegistrationInfo::id)
                .toList();
        boolean ownsActiveAccessor = accessors.contains(plan.accessorId());
        boolean wasEnabled = plan.plugins().contains(new PluginSelection(pluginId, true));
        if (wasEnabled == enabled && (enabled || !ownsActiveAccessor)) {
            return Optional.empty();
        }

        List<PluginSelection> selections = selectionsWith(plan, new PluginSelection(pluginId, enabled));
        String accessor = plan.accessorId();
        if (enabled && !ownsActiveAccessor && !accessors.isEmpty()) {
            accessor = accessors.getFirst();
        } else if (!enabled && ownsActiveAccessor) {
            accessor = fallbackAccessor(selections);
        }
        return Optional.of(new SimulationPlan(accessor, selections, plan.pluginOptions()));
    }

    private List<PluginSelection> selectionsWith(SimulationPlan plan, PluginSelection updated) {
        var selections = new ArrayList<>(plan.plugins());
        int catalogIndex = catalogOrder.indexOf(updated.pluginId());
        int insertAt = selections.size();
        for (int i = 0; i < selections.size(); i++) {
            String id = selections.get(i).pluginId();
            if (id.equals(updated.pluginId())) {
                selections.set(i, updated);
                return selections;
            }
            if (insertAt == selections.size() && catalogOrder.indexOf(id) > catalogIndex) {
                insertAt = i;
            }
        }
        selections.add(insertAt, updated);
        return selections;
    }

    private String fallbackAccessor(List<PluginSelection> selections) {
        // An explicit standalone plan can use its initial accessor without a selection
        // entry for the owner. A switch must never fall back to an explicitly disabled owner.
        var defaultRegistration = registry.registrations().stream()
                .filter(registration -> registration.point() == ApiPoint.ACCESSOR
                        && registration.id().equals(defaultAccessorId))
                .findFirst().orElseThrow();
        if (!selections.contains(new PluginSelection(defaultRegistration.pluginId(), false))) {
            return defaultAccessorId;
        }
        return registry.activeRegistrations(selections, ApiPoint.ACCESSOR).stream()
                .map(RegistrationInfo::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No accessor remains enabled"));
    }
}
