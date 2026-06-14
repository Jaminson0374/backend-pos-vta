package co.posinvent.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseReturn(
        UUID id,
        UUID receiptId,
        LocalDate returnDate,
        String documentNumber,
        String reason,
        PurchaseReturnStatus status,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version,
        List<PurchaseReturnLine> lines
) {
    public PurchaseReturn {
        if (lines == null) lines = List.of();
    }
}
