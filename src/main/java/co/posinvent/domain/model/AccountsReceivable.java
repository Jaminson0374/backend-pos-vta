package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountsReceivable(
        UUID id,
        UUID clientId,
        UUID documentId,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        LocalDate dueDate,
        ArStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        BigDecimal interestRate,
        BigDecimal interestAmount,
        LocalDate lastInterestCalcDate
) {
    public enum ArStatus { OPEN, PARTIAL, PAID, OVERDUE }

    public AccountsReceivable {
        if (paidAmount == null) paidAmount = BigDecimal.ZERO;
        if (interestAmount == null) interestAmount = BigDecimal.ZERO;
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
     *
     * <p>The fully-covered check runs first so a receivable with nothing left to collect is PAID
     * even when nothing was ever paid: {@code (0, 0)} is a fully returned receivable. A positive
     * total with no payment still reports OPEN.</p>
     */
    public static ArStatus computeStatus(BigDecimal totalAmount, BigDecimal paidAmount) {
        var t = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        var p = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        if (p.compareTo(t) >= 0) return ArStatus.PAID;
        if (p.compareTo(BigDecimal.ZERO) == 0) return ArStatus.OPEN;
        return ArStatus.PARTIAL;
    }
}
