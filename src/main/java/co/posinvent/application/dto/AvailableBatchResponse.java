package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single batch that can be reserved for a given product + warehouse, with its
 * remaining available quantity (current - committed).
 *
 * <p>Returned in FEFO order (earliest expiration first, rows without expiration
 * last) by the available-batches query.</p>
 */
public record AvailableBatchResponse(
        UUID batchId,
        LocalDate expirationDate,
        BigDecimal availableQuantity
) {
}
