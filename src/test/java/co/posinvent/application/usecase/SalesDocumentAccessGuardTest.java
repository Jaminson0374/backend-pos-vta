package co.posinvent.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Object-level authorization rules for sales documents: ADMIN and CONTADOR read everything; a
 * CAJERO reads only documents they created or whose shift they effectively own. Operation is
 * narrower: ADMIN, creator or effective shift owner, and CONTADOR gets no blanket permission.
 * Every denial must leave one structured audit WARN.
 */
@ExtendWith(MockitoExtension.class)
class SalesDocumentAccessGuardTest {

    private static final UUID DOC_ID = UUID.randomUUID();
    private static final UUID SHIFT_ID = UUID.randomUUID();
    private static final UUID CASH_REGISTER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID FOREIGN_ID = UUID.randomUUID();

    @Mock private ShiftRepository shiftRepository;

    private SalesDocumentAccessGuard guard;
    private final Logger auditLogger =
            (Logger) LoggerFactory.getLogger("co.posinvent.application.usecase.SecurityAuditLogger");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        guard = new SalesDocumentAccessGuard(shiftRepository);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void creatorCanReadWithoutLoadingTheShift() {
        var actor = actor("CAJERO");

        assertThatCode(() -> guard.requireRead(actor, document(ACTOR_ID, null), "READ_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        verifyNoInteractions(shiftRepository);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void assigneeCanReadDocumentsOfTheShiftTheyEffectivelyOwn() {
        var actor = actor("CAJERO");
        when(shiftRepository.findById(SHIFT_ID))
                .thenReturn(Optional.of(shift(FOREIGN_ID, ACTOR_ID)));

        assertThatCode(() -> guard.requireRead(actor, document(FOREIGN_ID, SHIFT_ID), "READ_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void openerCanReadDocumentsWhenShiftHasNoAssignee() {
        var actor = actor("CAJERO");
        when(shiftRepository.findById(SHIFT_ID))
                .thenReturn(Optional.of(shift(ACTOR_ID, null)));

        assertThatCode(() -> guard.requireRead(actor, document(FOREIGN_ID, SHIFT_ID), "READ_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void adminCanReadForeignDocumentsEvenWithoutAShift() {
        assertThatCode(() -> guard.requireRead(actor("ADMIN"), document(FOREIGN_ID, null), "READ_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        verifyNoInteractions(shiftRepository);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void contadorCanReadForeignDocumentsEvenWithoutAShift() {
        assertThatCode(() -> guard.requireRead(actor("CONTADOR"), document(FOREIGN_ID, null), "READ_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        verifyNoInteractions(shiftRepository);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void foreignCashierIsDeniedAndAudited() {
        assertThatThrownBy(() -> guard.requireRead(actor("CAJERO"), document(FOREIGN_ID, null), "READ_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos");

        assertDeniedAuditContains("CAJERO", "READ_SALES_DOCUMENT");
    }

    @Test
    void missingShiftFailsClosedAndIsAudited() {
        when(shiftRepository.findById(SHIFT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireRead(actor("CAJERO"), document(FOREIGN_ID, SHIFT_ID), "READ_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class);

        assertDeniedAuditContains("CAJERO", "READ_SALES_DOCUMENT");
    }

    @Test
    void shiftWithoutEffectiveOwnerFailsClosed() {
        when(shiftRepository.findById(SHIFT_ID))
                .thenReturn(Optional.of(shift(null, null)));

        assertThatThrownBy(() -> guard.requireRead(actor("CAJERO"), document(FOREIGN_ID, SHIFT_ID), "READ_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class);

        assertDeniedAuditContains("CAJERO", "READ_SALES_DOCUMENT");
    }

    @Test
    void creatorMatchDoesNotLoadTheShift() {
        var actor = actor("CAJERO");

        guard.requireRead(actor, document(ACTOR_ID, SHIFT_ID), "READ_SALES_DOCUMENT");

        verify(shiftRepository, never()).findById(SHIFT_ID);
        assertThat(appender.list).isEmpty();
    }

    // ── requireOperate ────────────────────────────────────────────────────

    @Test
    void adminCanOperateForeignDocumentsEvenWithoutAShift() {
        assertThatCode(() -> guard.requireOperate(
                actor("ADMIN"), document(FOREIGN_ID, null), "TRANSITION_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        verifyNoInteractions(shiftRepository);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void creatorCanOperateWithoutLoadingTheShift() {
        guard.requireOperate(
                actor("CAJERO"), document(ACTOR_ID, SHIFT_ID), "TRANSITION_SALES_DOCUMENT");

        verify(shiftRepository, never()).findById(SHIFT_ID);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void shiftAssigneeCanOperateTheShiftDocuments() {
        when(shiftRepository.findById(SHIFT_ID))
                .thenReturn(Optional.of(shift(FOREIGN_ID, ACTOR_ID)));

        assertThatCode(() -> guard.requireOperate(
                actor("CAJERO"), document(FOREIGN_ID, SHIFT_ID), "TRANSITION_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void shiftOpenerCanOperateWhenShiftHasNoAssignee() {
        when(shiftRepository.findById(SHIFT_ID))
                .thenReturn(Optional.of(shift(ACTOR_ID, null)));

        assertThatCode(() -> guard.requireOperate(
                actor("CAJERO"), document(FOREIGN_ID, SHIFT_ID), "TRANSITION_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void adminAndContadorCanOperate() {
        var actor = new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of("ADMIN", "CONTADOR"));

        assertThatCode(() -> guard.requireOperate(
                actor, document(FOREIGN_ID, null), "TRANSITION_SALES_DOCUMENT"))
                .doesNotThrowAnyException();

        assertThat(appender.list).isEmpty();
    }

    @Test
    void contadorIsDeniedOperationEvenWhenCreator() {
        assertThatThrownBy(() -> guard.requireOperate(
                actor("CONTADOR"), document(ACTOR_ID, null), "TRANSITION_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos para operar");

        assertDeniedAuditContains("CONTADOR", "TRANSITION_SALES_DOCUMENT");
    }

    @Test
    void contadorIsDeniedOperationEvenOnForeignDocuments() {
        assertThatThrownBy(() -> guard.requireOperate(
                actor("CONTADOR"), document(FOREIGN_ID, null), "TRANSITION_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos para operar");

        assertDeniedAuditContains("CONTADOR", "TRANSITION_SALES_DOCUMENT");
    }

    @Test
    void foreignCashierIsDeniedOperationAndAudited() {
        assertThatThrownBy(() -> guard.requireOperate(
                actor("CAJERO"), document(FOREIGN_ID, null), "TRANSITION_SALES_DOCUMENT"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene permisos para operar");

        assertDeniedAuditContains("CAJERO", "TRANSITION_SALES_DOCUMENT");
    }

    private void assertDeniedAuditContains(String role, String operation) {
        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .startsWith("[Acceso Denegado]")
                .contains(ACTOR_ID.toString())
                .contains(role)
                .contains(operation)
                .contains("SalesDocument")
                .contains(DOC_ID.toString());
    }

    private static CurrentActorProvider.CurrentActor actor(String role) {
        return new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of(role));
    }

    private static SalesDocument document(UUID createdBy, UUID shiftId) {
        return new SalesDocument(
                DOC_ID, SalesDocumentType.ORDER, SalesDocumentStatus.DRAFT, "ORD-TEST",
                CLIENT_ID, WAREHOUSE_ID, shiftId, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                createdBy, null, null, List.of(), null, false, null);
    }

    private static Shift shift(UUID userId, UUID assignedTo) {
        return new Shift(
                SHIFT_ID, CASH_REGISTER_ID, userId, assignedTo, null,
                OffsetDateTime.now().minusHours(1), null,
                BigDecimal.ZERO, null, ShiftStatus.OPEN, null, OffsetDateTime.now());
    }
}
