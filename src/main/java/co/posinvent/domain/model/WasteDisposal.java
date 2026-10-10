package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WasteDisposal(
        UUID id,
        UUID productId,
        UUID batchId,
        UUID warehouseId,
        DisposalType dispositionType,
        BigDecimal quantity,
        BigDecimal unitCost,
        String reason,
        String officialDocument,
        LocalDate disposalDate,
        UUID journalEntryId,
        UUID registeredBy,
        OffsetDateTime createdAt
) {}
