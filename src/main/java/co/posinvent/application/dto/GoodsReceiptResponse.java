package co.posinvent.application.dto;

import co.posinvent.domain.model.GoodsReceipt;
import co.posinvent.domain.service.ReceiptDomainService.CostDeviation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
        UUID id,
        UUID ocId,
        LocalDate receiptDate,
        List<UUID> batchIds,
        List<LocalDate> expirationDates,
        List<Deviation> deviations
) {
    public record Deviation(
            UUID productId,
            BigDecimal ocUnitCost,
            BigDecimal actualCost,
            BigDecimal deviationPct
    ) {}

    public static GoodsReceiptResponse from(
            GoodsReceipt receipt,
            List<UUID> batchIds,
            List<LocalDate> expirationDates,
            List<CostDeviation> deviations
    ) {
        return new GoodsReceiptResponse(
                receipt.id(),
                receipt.ocId(),
                receipt.receiptDate(),
                batchIds,
                expirationDates,
                deviations.stream()
                        .map(d -> new Deviation(d.productId(), d.ocUnitCost(), d.actualCost(), d.deviationPct()))
                        .toList()
        );
    }
}
