package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseReturnLine(
        UUID id,
        UUID returnId,
        UUID productId,
        UUID warehouseId,
        UUID batchId,
        BigDecimal returnQty,
        BigDecimal unitCost,
        int lineNumber
) {}
