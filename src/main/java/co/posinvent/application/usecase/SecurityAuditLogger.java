package co.posinvent.application.usecase;

import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured audit trail for object-level authorization denials. Only UUIDs and roles are
 * logged: never tokens, passwords or other personal data.
 */
final class SecurityAuditLogger {

    public static final String LOGGER_NAME = "co.posinvent.application.usecase.SecurityAuditLogger";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private SecurityAuditLogger() {
    }

    static void denied(CurrentActorProvider.CurrentActor actor, String operation, Object object) {
        denied(actor, operation, "Shift", object);
    }

    static void denied(CurrentActorProvider.CurrentActor actor, String operation,
                       String objectType, Object object) {
        log.warn("[Acceso Denegado] Actor: {} | Rol: {} | Intento de Operación: {} | Objeto ({}): {}",
                actor.userId(), actor.roles(), operation, objectType, object);
    }
}
