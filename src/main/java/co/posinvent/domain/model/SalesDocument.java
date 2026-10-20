package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.InvalidStateTransitionException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SalesDocument(
        UUID id,
        SalesDocumentType type,
        SalesDocumentStatus status,
        String documentNumber,
        UUID clientId,
        UUID warehouseId,
        UUID shiftId,
        UUID cashRegisterId,
        UUID sourceDocumentId,
        BigDecimal totalNet,
        BigDecimal totalTax0,
        BigDecimal totalTax5,
        BigDecimal totalTax19,
        BigDecimal totalAmount,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SaleItem> items,
        LocalDate dueDate,
        Boolean isCreditSale,
        String reason,
        BigDecimal discountTotal,
        BigDecimal documentDiscount,
        String documentDiscountType,
        BigDecimal documentDiscountValue,
        UUID discountAuthorizedBy,
        String discountReason
) {
    public static final String DISCOUNT_NONE = "NONE";

    public SalesDocument {
        if (items == null) items = List.of();
        if (isCreditSale == null) isCreditSale = false;
        if (discountTotal == null) discountTotal = BigDecimal.ZERO;
        if (documentDiscount == null) documentDiscount = BigDecimal.ZERO;
        if (documentDiscountType == null || documentDiscountType.isBlank()) documentDiscountType = DISCOUNT_NONE;
        if (documentDiscountValue == null) documentDiscountValue = BigDecimal.ZERO;
    }

    /**
     * Validates the state-machine rule for this document and returns a new instance with the
     * target status. Every other component is preserved; the entity stays immutable.
     *
     * <p>This is the user-facing rule only: it deliberately knows nothing about actors or roles.
     * The application layer performs the object-level authorization before calling it.</p>
     */
    public SalesDocument transitionTo(SalesDocumentStatus nextStatus) {
        validateTransition(nextStatus);
        return new SalesDocument(
                id, type, nextStatus, documentNumber, clientId, warehouseId, shiftId,
                cashRegisterId, sourceDocumentId, totalNet, totalTax0, totalTax5, totalTax19,
                totalAmount, createdBy, createdAt, updatedAt, items, dueDate, isCreditSale,
                reason, discountTotal, documentDiscount, documentDiscountType,
                documentDiscountValue, discountAuthorizedBy, discountReason);
    }

    private void validateTransition(SalesDocumentStatus target) {
        if (type == SalesDocumentType.QUOTE) {
            validateQuoteTransition(target);
            return;
        }
        if (type == SalesDocumentType.ORDER) {
            validateOrderTransition(target);
            return;
        }
        if (type == SalesDocumentType.INVOICE
                || type == SalesDocumentType.CREDIT_NOTE
                || type == SalesDocumentType.DEBIT_NOTE) {
            validateInvoiceTransition(target);
            return;
        }
        throw invalidTransition(target);
    }

    private void validateQuoteTransition(SalesDocumentStatus target) {
        if (target == SalesDocumentStatus.SENT) {
            if (status != SalesDocumentStatus.DRAFT) {
                throw invalidTransition(target);
            }
            if (items.isEmpty()) {
                throw new BusinessException("SD_NO_ITEMS",
                        "La cotización debe tener al menos un ítem para ser enviada");
            }
            return;
        }
        if (target == SalesDocumentStatus.ACCEPTED
                || target == SalesDocumentStatus.REJECTED
                || target == SalesDocumentStatus.EXPIRED) {
            if (status != SalesDocumentStatus.SENT) {
                throw invalidTransition(target);
            }
            return;
        }
        throw invalidTransition(target);
    }

    private void validateOrderTransition(SalesDocumentStatus target) {
        if (target == SalesDocumentStatus.CONFIRMED) {
            if (status != SalesDocumentStatus.DRAFT) {
                throw invalidTransition(target);
            }
            if (items.isEmpty()) {
                throw new BusinessException("SD_NO_ITEMS",
                        "El pedido debe tener al menos un ítem para confirmar");
            }
            return;
        }
        if (target == SalesDocumentStatus.CANCELLED) {
            if (status != SalesDocumentStatus.DRAFT && status != SalesDocumentStatus.CONFIRMED) {
                throw invalidTransition(target);
            }
            return;
        }
        if (target == SalesDocumentStatus.INVOICED
                || target == SalesDocumentStatus.PARTIALLY_INVOICED) {
            if (status != SalesDocumentStatus.CONFIRMED
                    && status != SalesDocumentStatus.PARTIALLY_INVOICED) {
                throw invalidTransition(target);
            }
            return;
        }
        throw invalidTransition(target);
    }

    private void validateInvoiceTransition(SalesDocumentStatus target) {
        if (target == SalesDocumentStatus.ISSUED) {
            if (status != SalesDocumentStatus.DRAFT) {
                throw invalidTransition(target);
            }
            return;
        }
        if (target == SalesDocumentStatus.PAID || target == SalesDocumentStatus.CANCELLED) {
            if (status != SalesDocumentStatus.ISSUED) {
                throw invalidTransition(target);
            }
            return;
        }
        throw invalidTransition(target);
    }

    private InvalidStateTransitionException invalidTransition(SalesDocumentStatus target) {
        return new InvalidStateTransitionException(type, status, target);
    }

    /** Backwards-compatible constructor for documents without discounts. */
    public SalesDocument(
            UUID id,
            SalesDocumentType type,
            SalesDocumentStatus status,
            String documentNumber,
            UUID clientId,
            UUID warehouseId,
            UUID shiftId,
            UUID cashRegisterId,
            UUID sourceDocumentId,
            BigDecimal totalNet,
            BigDecimal totalTax0,
            BigDecimal totalTax5,
            BigDecimal totalTax19,
            BigDecimal totalAmount,
            UUID createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<SaleItem> items,
            LocalDate dueDate,
            Boolean isCreditSale,
            String reason
    ) {
        this(id, type, status, documentNumber, clientId, warehouseId, shiftId, cashRegisterId,
                sourceDocumentId, totalNet, totalTax0, totalTax5, totalTax19, totalAmount,
                createdBy, createdAt, updatedAt, items, dueDate, isCreditSale, reason,
                BigDecimal.ZERO, BigDecimal.ZERO, DISCOUNT_NONE, BigDecimal.ZERO, null, null);
    }
}
