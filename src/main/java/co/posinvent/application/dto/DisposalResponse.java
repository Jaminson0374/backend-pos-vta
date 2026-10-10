package co.posinvent.application.dto;

import co.posinvent.domain.model.WasteDisposal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DisposalResponse(
        UUID id, UUID productId, UUID batchId, UUID warehouseId,
        String disposalType, BigDecimal quantity, BigDecimal unitCost,
        String reason, String officialDocument, LocalDate disposalDate,
        UUID journalEntryId, UUID registeredBy, OffsetDateTime createdAt
) {
    public static DisposalResponse from(WasteDisposal d) {
        return new DisposalResponse(d.id(), d.productId(), d.batchId(), d.warehouseId(),
                d.dispositionType().name(), d.quantity(), d.unitCost(), d.reason(),
                d.officialDocument(), d.disposalDate(), d.journalEntryId(), d.registeredBy(), d.createdAt());
    }
}
