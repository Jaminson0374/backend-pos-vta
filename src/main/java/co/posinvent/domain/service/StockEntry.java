package co.posinvent.domain.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable value object describing an incoming stock movement to be costed.
 */
public record StockEntry(
        UUID productId,
        UUID batchId,
        UUID warehouseId,
        BigDecimal quantity,
        BigDecimal unitCost,
        UUID sourceMovementId,
        OffsetDateTime entryDate
) {}
