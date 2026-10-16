package co.posinvent.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GoodsReceipt(
        UUID id,
        UUID ocId,
        LocalDate receiptDate,
        GoodsReceiptStatus status,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Long version,
        List<ReceiptLineItem> lines,
        List<UUID> batchIds,
        String idempotencyKey
) {
    public GoodsReceipt {
        if (lines == null) lines = List.of();
        if (batchIds == null) batchIds = List.of();
    }
}
