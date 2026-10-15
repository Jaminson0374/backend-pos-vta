package co.posinvent.application.dto;

import co.posinvent.domain.model.CashCount;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Historical view of a persisted cash count (arqueo), including its
 * per-denomination breakdown so the POS can render past counts.
 */
public record CashCountResponse(
        UUID id,
        UUID shiftId,
        BigDecimal expectedTotal,
        BigDecimal countedTotal,
        BigDecimal difference,
        OffsetDateTime createdAt,
        List<CashCountLineResponse> lines
) {
    public static CashCountResponse from(CashCount cashCount) {
        var lines = cashCount.lines().stream()
                .map(CashCountLineResponse::from)
                .toList();
        return new CashCountResponse(
                cashCount.id(),
                cashCount.shiftId(),
                cashCount.expectedTotal(),
                cashCount.countedTotal(),
                cashCount.difference(),
                cashCount.createdAt(),
                lines);
    }

    public record CashCountLineResponse(
            BigDecimal denominationValue,
            String denominationType,
            int quantity,
            BigDecimal subtotal
    ) {
        public static CashCountLineResponse from(CashCount.Line line) {
            return new CashCountLineResponse(
                    line.denominationValue(),
                    line.denominationType(),
                    line.quantity(),
                    line.subtotal());
        }
    }
}
