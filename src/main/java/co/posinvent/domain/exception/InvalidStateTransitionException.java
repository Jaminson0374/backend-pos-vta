package co.posinvent.domain.exception;

import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;

/**
 * Raised when a sales document is asked to move to a status the state machine does not allow.
 *
 * <p>Extends {@link BusinessException} with the {@code SD_INVALID_TRANSITION} code so the global
 * handler maps it to the same 409 response as the rest of the document business rules.</p>
 */
public class InvalidStateTransitionException extends BusinessException {

    public static final String ERROR_CODE = "SD_INVALID_TRANSITION";

    public InvalidStateTransitionException(SalesDocumentType type,
                                           SalesDocumentStatus from,
                                           SalesDocumentStatus to) {
        super(ERROR_CODE, "Transición inválida para " + type + ": " + from + " → " + to);
    }
}
