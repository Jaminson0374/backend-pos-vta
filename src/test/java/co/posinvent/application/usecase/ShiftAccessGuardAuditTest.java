package co.posinvent.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused unit test for the structured denial audit trail: every object-level shift
 * denial must emit one WARN carrying the actor, roles, operation and shift id.
 */
class ShiftAccessGuardAuditTest {

    private static final UUID SHIFT_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    private final ShiftAccessGuard guard = new ShiftAccessGuard();
    private final Logger auditLogger =
            (Logger) LoggerFactory.getLogger("co.posinvent.application.usecase.SecurityAuditLogger");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void requireOperateLogsActorRolesOperationAndShift() {
        var actor = new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of("CAJERO"));

        assertThatThrownBy(() -> guard.requireOperate(foreignShift(), actor))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .startsWith("[Acceso Denegado]")
                .contains(ACTOR_ID.toString())
                .contains("CAJERO")
                .contains("OPERATE")
                .contains(SHIFT_ID.toString());
    }

    @Test
    void requireReadLogsTheReadOperation() {
        var actor = new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of("CAJERO"));

        assertThatThrownBy(() -> guard.requireRead(foreignShift(), actor))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .startsWith("[Acceso Denegado]")
                .contains(ACTOR_ID.toString())
                .contains("CAJERO")
                .contains("READ")
                .contains(SHIFT_ID.toString());
    }

    private static Shift foreignShift() {
        return new Shift(
                SHIFT_ID, CASH_REGISTER_ID, OWNER_ID, null, null,
                OffsetDateTime.now().minusHours(1), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null, OffsetDateTime.now());
    }
}
