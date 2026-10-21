package co.posinvent.application.usecase;

import co.posinvent.application.dto.WarehouseAccessResponse;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Returns the warehouse access of the caller. Used by the frontend to render only the logistics
 * actions the backend will accept.
 */
@Service
public class GetCurrentWarehouseAccessUseCase {

    private final CurrentActorProvider actorProvider;
    private final WarehouseAccessGuard accessGuard;

    public GetCurrentWarehouseAccessUseCase(
            CurrentActorProvider actorProvider,
            WarehouseAccessGuard accessGuard
    ) {
        this.actorProvider = actorProvider;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public WarehouseAccessResponse execute() {
        var access = accessGuard.memberships(actorProvider.current());
        return new WarehouseAccessResponse(access.unrestricted(), access.warehouseIds());
    }
}
