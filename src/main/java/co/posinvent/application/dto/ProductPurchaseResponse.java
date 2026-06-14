package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPurchaseResponse(
        UUID productId,
        String productName,
        String productCode,
        BigDecimal totalQty,
        BigDecimal totalCost,
        BigDecimal avgUnitCost
) {}
