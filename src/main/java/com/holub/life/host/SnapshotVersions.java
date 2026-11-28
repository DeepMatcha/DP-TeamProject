package com.holub.life.host;

import com.holub.life.api.ApiPoint;
import com.holub.life.api.GameSnapshot;
import java.util.HashSet;
import java.util.Map;

/**
 * Restore policy: a snapshot may only be restored with the plugin versions it was saved with.
 * Every recorded version must match the installed one, and every installed plugin the snapshot
 * depends on (in its plan, as a cell type's owner, or as the accessor's owner) must be recorded.
 */
final class SnapshotVersions {
    private SnapshotVersions() {}

    static void check(GameSnapshot snapshot, HostRegistry registry) {
        Map<String, String> installed = registry.pluginVersions();
        snapshot.pluginVersions().forEach((id, version) -> {
            if (!version.equals(installed.get(id))) {
                throw new IllegalArgumentException("Incompatible plugin version: " + id);
            }
        });
        var referenced = new HashSet<String>();
        snapshot.plan().plugins().forEach(selection -> referenced.add(selection.pluginId()));
        var usedTypes = new HashSet<String>();
        snapshot.board().cells().forEach(cell -> usedTypes.add(cell.type().id()));
        for (var registration : registry.registrations()) {
            if ((registration.point() == ApiPoint.CELL_TYPE && usedTypes.contains(registration.id()))
                    || (registration.point() == ApiPoint.ACCESSOR
                        && registration.id().equals(snapshot.plan().accessorId()))) {
                referenced.add(registration.pluginId());
            }
        }
        for (String id : referenced) {
            if (installed.containsKey(id) && !snapshot.pluginVersions().containsKey(id)) {
                throw new IllegalArgumentException("Missing plugin version: " + id);
            }
        }
    }
}
