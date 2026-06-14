package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountsPayable(
        UUID id,
        UUID supplierId,
        UUID documentId,
        UUID debitCreditNoteId,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        LocalDate dueDate,
        ApStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public enum ApStatus { OPEN, PARTIAL, PAID, OVERDUE }

    public AccountsPayable {
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
    }

    /**
     * Computes outstanding as totalAmount - paidAmount.
     */
    public static BigDecimal computeOutstanding(BigDecimal totalAmount, BigDecimal paidAmount) {
        var t = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        var p = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        return t.subtract(p);
    }

    /**
     * Determines status from paid amount vs total.
     */
    public static ApStatus computeStatus(BigDecimal totalAmount, BigDecimal paidAmount) {
        var t = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        var p = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        if (p.compareTo(BigDecimal.ZERO) == 0) return ApStatus.OPEN;
        if (p.compareTo(t) >= 0) return ApStatus.PAID;
        return ApStatus.PARTIAL;
    }
}
