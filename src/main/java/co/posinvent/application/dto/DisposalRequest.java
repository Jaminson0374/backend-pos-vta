package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DisposalRequest(
        UUID productId, UUID batchId, UUID warehouseId,
        String disposalType, BigDecimal quantity, String reason,
        String officialDocument, LocalDate disposalDate
) {}
