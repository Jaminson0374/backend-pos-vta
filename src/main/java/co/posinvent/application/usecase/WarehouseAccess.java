package co.posinvent.application.usecase;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Warehouse visibility of an actor within a single use-case invocation. {@code unrestricted}
 * short-circuits every check (ADMIN); otherwise only the listed warehouse ids are accessible.
 */
public record WarehouseAccess(boolean unrestricted, Set<UUID> warehouseIds) {

    public WarehouseAccess {
        warehouseIds = warehouseIds == null ? Set.of() : Set.copyOf(warehouseIds);
    }

    public boolean canAccess(Collection<UUID> candidates) {
        if (unrestricted) {
            return true;
        }
        if (candidates == null || warehouseIds.isEmpty()) {
            return false;
        }
        for (UUID candidate : candidates) {
            if (warehouseIds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
