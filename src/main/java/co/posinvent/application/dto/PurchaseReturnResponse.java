package co.posinvent.application.dto;

import co.posinvent.domain.model.PurchaseReturn;
import co.posinvent.domain.model.PurchaseReturnLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnResponse(
        UUID id,
        UUID receiptId,
        LocalDate returnDate,
        String documentNumber,
        String reason,
        String status,
        List<LineResponse> lines,
        int affectedBatches,
        BigDecimal totalReturned
) {
    public record LineResponse(
            UUID productId,
            UUID warehouseId,
            UUID batchId,
            BigDecimal returnQty,
            BigDecimal unitCost,
            int lineNumber
    ) {}

    public static PurchaseReturnResponse from(
            PurchaseReturn pr,
            int affectedBatches,
            BigDecimal totalReturned
    ) {
        return new PurchaseReturnResponse(
                pr.id(),
                pr.receiptId(),
                pr.returnDate(),
                pr.documentNumber(),
                pr.reason(),
                pr.status().name(),
                pr.lines().stream()
                        .map(l -> new LineResponse(
                                l.productId(), l.warehouseId(), l.batchId(),
                                l.returnQty(), l.unitCost(), l.lineNumber()
                        ))
                        .toList(),
                affectedBatches,
                totalReturned
        );
    }
}
