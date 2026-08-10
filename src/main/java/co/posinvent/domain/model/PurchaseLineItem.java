package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseLineItem(
        UUID id,
        UUID ocId,
        UUID productId,
        UUID warehouseId,
        BigDecimal orderedQty,
        BigDecimal receivedQty,
        BigDecimal unitCost,
        BigDecimal discountPct,
        String taxType,
        int lineNumber
) {}
