package co.posinvent.application.usecase;

import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Object-level read rules for sales documents.
 *
 * <p>ADMIN and CONTADOR may read every document. A CAJERO may read only documents they
 * created or documents that belong to a shift they effectively own
 * ({@code assigned_to}, falling back to {@code user_id}). Everything else fails closed.</p>
 */
@Component
public class SalesDocumentAccessGuard {

    private final ShiftRepository shiftRepository;

    public SalesDocumentAccessGuard(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public void requireRead(CurrentActorProvider.CurrentActor actor, SalesDocument document, String operation) {
        if (actor.isAdmin() || actor.isContador()) {
            return;
        }

        if (actor.userId() != null && actor.userId().equals(document.createdBy())) {
            return;
        }

        if (document.shiftId() != null) {
            var shift = shiftRepository.findById(document.shiftId()).orElse(null);
            if (shift != null
                    && shift.effectiveOwnerId() != null
                    && shift.effectiveOwnerId().equals(actor.userId())) {
                return;
            }
        }

        SecurityAuditLogger.denied(actor, operation, "SalesDocument", document.id());
        throw new AccessDeniedException("No tiene permisos para acceder a este documento.");
    }
}
