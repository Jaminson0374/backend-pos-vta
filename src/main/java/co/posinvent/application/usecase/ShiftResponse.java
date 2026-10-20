package co.posinvent.application.usecase;

import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ShiftResponse(
        UUID id,
        UUID cashRegisterId,
        UUID userId,
        UUID assignedTo,
        UUID closedBy,
        OffsetDateTime openingTime,
        OffsetDateTime closingTime,
        BigDecimal openingAmount,
        BigDecimal closingAmount,
        ShiftStatus status,
        String zReportUrl,
        OffsetDateTime createdAt
) {
    public static ShiftResponse from(Shift shift) {
        return new ShiftResponse(
                shift.id(),
                shift.cashRegisterId(),
                shift.userId(),
                shift.assignedTo(),
                shift.closedBy(),
                shift.openingTime(),
                shift.closingTime(),
                shift.openingAmount(),
                shift.closingAmount(),
                shift.status(),
                shift.zReportUrl(),
                shift.createdAt()
        );
    }
}
