package co.posinvent.application.usecase;

import co.posinvent.domain.repository.UserWarehouseRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Warehouse membership rules for object-level authorization.
 *
 * <p>ADMIN is unrestricted and never queries memberships. Any other actor receives the set of
 * warehouses assigned to them; callers must load it once per use-case invocation and reuse it
 * instead of querying per row.</p>
 */
@Component
public class WarehouseAccessGuard {

    private final UserWarehouseRepository membershipRepo;

    public WarehouseAccessGuard(UserWarehouseRepository membershipRepo) {
        this.membershipRepo = membershipRepo;
    }

    public WarehouseAccess memberships(CurrentActorProvider.CurrentActor actor) {
        if (actor.isAdmin()) {
            return new WarehouseAccess(true, Set.of());
        }
        return new WarehouseAccess(false,
                Set.copyOf(membershipRepo.findWarehouseIdsByUserId(actor.userId())));
    }

    public void requireAny(
            CurrentActorProvider.CurrentActor actor,
            WarehouseAccess access,
            Collection<UUID> warehouseIds,
            String operation,
            Object objectId
    ) {
        if (access.canAccess(warehouseIds)) {
            return;
        }
        SecurityAuditLogger.denied(actor, operation, "StockTransfer", objectId);
        throw new AccessDeniedException("No tiene acceso a la bodega de este traslado.");
    }
}
