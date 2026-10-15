package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Historical record of a cash count (arqueo) performed for a {@link Shift}.
 *
 * <p>{@code countedTotal} is the sum of every denomination line's subtotal,
 * {@code expectedTotal} is the shift's NET document total (invoices plus negative
 * credit notes, see audit A2) and {@code difference} is
 * {@code countedTotal - expectedTotal}. The per-denomination {@code lines} are
 * preserved so the count survives after the POS screen is closed.</p>
 */
public record CashCount(
        UUID id,
        UUID shiftId,
        BigDecimal expectedTotal,
        BigDecimal countedTotal,
        BigDecimal difference,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<Line> lines
) {
    public CashCount {
        if (lines == null) lines = List.of();
    }

    /** One counted denomination: {@code subtotal = denominationValue * quantity}. */
    public record Line(
            UUID id,
            BigDecimal denominationValue,
            String denominationType,
            int quantity,
            BigDecimal subtotal
    ) {
    }
}
