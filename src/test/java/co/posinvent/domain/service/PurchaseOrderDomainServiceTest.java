package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseOrderDomainServiceTest {

    private static final List<PurchaseOrderStatus> ALL_STATUSES = List.of(PurchaseOrderStatus.values());

    private PurchaseOrderDomainService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderDomainService();
    }

    @Test
    void shouldAllowAllDefinedTransitions() {
        assertThatCode(() -> service.validateTransition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.PARTIAL))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateTransition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.RECEIVED))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateTransition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.CANCELLED))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateTransition(PurchaseOrderStatus.PARTIAL, PurchaseOrderStatus.RECEIVED))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.validateTransition(PurchaseOrderStatus.PARTIAL, PurchaseOrderStatus.CANCELLED))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldTreatSameStateTransitionAsNoOp() {
        for (var status : ALL_STATUSES) {
            assertThatCode(() -> service.validateTransition(status, status))
                    .as("same-state transition %s -> %s", status, status)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void shouldRejectReceivedToPartial() {
        assertInvalidTransition(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PARTIAL);
    }

    @Test
    void shouldRejectCancelledToPending() {
        assertInvalidTransition(PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.PENDING);
    }

    @Test
    void shouldRejectPartialToPending() {
        assertInvalidTransition(PurchaseOrderStatus.PARTIAL, PurchaseOrderStatus.PENDING);
    }

    @Test
    void shouldRejectReceivedToPending() {
        assertInvalidTransition(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.PENDING);
    }

    @Test
    void shouldAcceptPositiveOrderedQuantity() {
        assertThatCode(() -> service.validateLineItem(BigDecimal.ONE)).doesNotThrowAnyException();
        assertThatCode(() -> service.validateLineItem(new BigDecimal("0.01"))).doesNotThrowAnyException();
        assertThatCode(() -> service.validateLineItem(new BigDecimal("1000"))).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectNullOrderedQuantity() {
        assertInvalidQuantity(null);
    }

    @Test
    void shouldRejectZeroOrderedQuantity() {
        assertInvalidQuantity(BigDecimal.ZERO);
    }

    @Test
    void shouldRejectNegativeOrderedQuantity() {
        assertInvalidQuantity(new BigDecimal("-1"));
        assertInvalidQuantity(new BigDecimal("-0.01"));
    }

    private void assertInvalidTransition(PurchaseOrderStatus from, PurchaseOrderStatus to) {
        assertThatThrownBy(() -> service.validateTransition(from, to))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "OC_INVALID_TRANSITION")
                .hasMessageContaining("Transición de estado no permitida");
    }

    private void assertInvalidQuantity(BigDecimal orderedQty) {
        assertThatThrownBy(() -> service.validateLineItem(orderedQty))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", "OC_INVALID_LINE_QTY")
                .hasMessageContaining("La cantidad ordenada debe ser mayor a cero");
    }
}
