package co.posinvent.application.usecase;

import co.posinvent.domain.model.Shift;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Object-level access rules for shifts.
 *
 * <p>Operating a shift (closing it or registering a cash count) is allowed only for an
 * ADMIN or for the shift's effective owner. Reading is also allowed for a CONTADOR.</p>
 */
@Component
public class ShiftAccessGuard {

    public void requireOperate(Shift shift, CurrentActorProvider.CurrentActor actor) {
        if (!isOwner(shift, actor) && !actor.isAdmin()) {
            SecurityAuditLogger.denied(actor, "OPERATE", shift.id());
            throw new AccessDeniedException("No tiene permisos para operar este turno.");
        }
    }

    public void requireRead(Shift shift, CurrentActorProvider.CurrentActor actor) {
        if (actor.isAdmin() || actor.isContador()) {
            return;
        }
        if (!isOwner(shift, actor)) {
            SecurityAuditLogger.denied(actor, "READ", shift.id());
            throw new AccessDeniedException("No tiene permisos para operar este turno.");
        }
    }

    private static boolean isOwner(Shift shift, CurrentActorProvider.CurrentActor actor) {
        return shift.effectiveOwnerId() != null
                && shift.effectiveOwnerId().equals(actor.userId());
    }
}
