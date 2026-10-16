package co.posinvent.domain.service;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Validación pura de dominio para órdenes de compra.
 * No tiene dependencias de infraestructura ni Spring.
 */
public final class PurchaseOrderDomainService {

    private static final Set<Transition> ALLOWED_TRANSITIONS = Set.of(
            new Transition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.PARTIAL),
            new Transition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.RECEIVED),
            new Transition(PurchaseOrderStatus.PENDING, PurchaseOrderStatus.CANCELLED),
            new Transition(PurchaseOrderStatus.PARTIAL, PurchaseOrderStatus.RECEIVED),
            new Transition(PurchaseOrderStatus.PARTIAL, PurchaseOrderStatus.CANCELLED)
    );

    /**
     * Valida que la transición de estado sea permitida.
     *
     * @throws BusinessException con código OC_INVALID_TRANSITION si la transición no es válida
     */
    public void validateTransition(PurchaseOrderStatus from, PurchaseOrderStatus to) {
        if (from == to) return;
        if (!ALLOWED_TRANSITIONS.contains(new Transition(from, to))) {
            throw new BusinessException(
                    "OC_INVALID_TRANSITION",
                    "Transición de estado no permitida: " + from + " → " + to
            );
        }
    }

    /**
     * Valida los datos de una línea de orden de compra.
     *
     * @throws BusinessException si la cantidad ordenada es ≤ 0
     */
    public void validateLineItem(BigDecimal orderedQty) {
        if (orderedQty == null || orderedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                    "OC_INVALID_LINE_QTY",
                    "La cantidad ordenada debe ser mayor a cero"
            );
        }
    }

    private record Transition(PurchaseOrderStatus from, PurchaseOrderStatus to) {}
}
