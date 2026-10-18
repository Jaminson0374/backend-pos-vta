package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Shift(
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
    /** The user who effectively operates this shift: the assignee when present, else the opener. */
    public UUID effectiveOwnerId() {
        return assignedTo != null ? assignedTo : userId;
    }
}
