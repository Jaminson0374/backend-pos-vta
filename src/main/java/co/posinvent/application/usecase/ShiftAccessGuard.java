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
        boolean owner = shift.effectiveOwnerId() != null
                && shift.effectiveOwnerId().equals(actor.userId());
        if (!actor.isAdmin() && !owner) {
            throw new AccessDeniedException("No tiene permisos para operar este turno.");
        }
    }

    public void requireRead(Shift shift, CurrentActorProvider.CurrentActor actor) {
        if (actor.isAdmin() || actor.isContador()) {
            return;
        }
        requireOperate(shift, actor);
    }
}
