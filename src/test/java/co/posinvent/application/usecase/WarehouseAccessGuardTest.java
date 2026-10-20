package co.posinvent.application.usecase;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.posinvent.domain.repository.UserWarehouseRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Warehouse membership rules: ADMIN is unrestricted without a query, a warehouse member may
 * operate on transfers involving any of their warehouses, and a foreign actor is denied with
 * exactly one structured audit WARN.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseAccessGuardTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_A = UUID.randomUUID();
    private static final UUID WAREHOUSE_B = UUID.randomUUID();
    private static final UUID OBJECT_ID = UUID.randomUUID();

    @Mock
    private UserWarehouseRepository membershipRepo;

    private WarehouseAccessGuard guard;

    private final Logger auditLogger =
            (Logger) LoggerFactory.getLogger(SecurityAuditLogger.LOGGER_NAME);
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        guard = new WarehouseAccessGuard(membershipRepo);
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
    void adminIsUnrestrictedWithoutQueryingMemberships() {
        var actor = actor("ADMIN");

        var access = guard.memberships(actor);

        assertThat(access.unrestricted()).isTrue();
        assertThat(access.warehouseIds()).isEmpty();
        verifyNoInteractions(membershipRepo);
        assertThatCode(() -> guard.requireAny(
                actor, access, List.of(WAREHOUSE_A), "READ_TRANSFER", OBJECT_ID))
                .doesNotThrowAnyException();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void memberOfAnInvolvedWarehouseIsAllowed() {
        var actor = actor("ALMACENISTA");
        when(membershipRepo.findWarehouseIdsByUserId(ACTOR_ID)).thenReturn(Set.of(WAREHOUSE_A));

        var access = guard.memberships(actor);

        assertThat(access.unrestricted()).isFalse();
        assertThat(access.warehouseIds()).containsExactly(WAREHOUSE_A);
        assertThat(access.canAccess(List.of(WAREHOUSE_A, WAREHOUSE_B))).isTrue();
        assertThatCode(() -> guard.requireAny(
                actor, access, List.of(WAREHOUSE_A, WAREHOUSE_B), "READ_TRANSFER", OBJECT_ID))
                .doesNotThrowAnyException();
        verify(membershipRepo, times(1)).findWarehouseIdsByUserId(ACTOR_ID);
        assertThat(appender.list).isEmpty();
    }

    @Test
    void nonMemberIsDeniedAndAudited() {
        var actor = actor("ALMACENISTA");
        when(membershipRepo.findWarehouseIdsByUserId(ACTOR_ID)).thenReturn(Set.of(WAREHOUSE_B));

        var access = guard.memberships(actor);

        assertThat(access.canAccess(List.of(WAREHOUSE_A))).isFalse();
        assertThatThrownBy(() -> guard.requireAny(
                actor, access, List.of(WAREHOUSE_A), "READ_TRANSFER", OBJECT_ID))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No tiene acceso a la bodega de este traslado.");

        assertDeniedAuditContains("READ_TRANSFER");
    }

    @Test
    void multipleWarehousesAreLoadedOncePerInvocation() {
        var actor = actor("ALMACENISTA");
        when(membershipRepo.findWarehouseIdsByUserId(ACTOR_ID))
                .thenReturn(Set.of(WAREHOUSE_A, WAREHOUSE_B));

        var access = guard.memberships(actor);

        assertThat(access.warehouseIds()).containsExactlyInAnyOrder(WAREHOUSE_A, WAREHOUSE_B);
        assertThat(access.canAccess(List.of(WAREHOUSE_B))).isTrue();
        assertThatCode(() -> guard.requireAny(
                actor, access, List.of(WAREHOUSE_B), "READ_TRANSFER", OBJECT_ID))
                .doesNotThrowAnyException();
        verify(membershipRepo, times(1)).findWarehouseIdsByUserId(ACTOR_ID);
    }

    private void assertDeniedAuditContains(String operation) {
        assertThat(appender.list).hasSize(1);
        var message = appender.list.get(0).getFormattedMessage();
        assertThat(message)
                .startsWith("[Acceso Denegado]")
                .contains(ACTOR_ID.toString())
                .contains("ALMACENISTA")
                .contains(operation)
                .contains("StockTransfer")
                .contains(OBJECT_ID.toString());
    }

    private static CurrentActorProvider.CurrentActor actor(String role) {
        return new CurrentActorProvider.CurrentActor(ACTOR_ID, Set.of(role));
    }
}
