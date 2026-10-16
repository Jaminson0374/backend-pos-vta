package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptRequest(
        @NotNull UUID ocId,
        @NotNull @NotEmpty @Valid List<ReceiptLine> lines,
        @Size(max = 100) String idempotencyKey
) {

    /**
     * Backwards-compatible constructor for callers that do not supply an idempotency key
     * (the key is optional; without it the receipt is not deduplicated).
     */
    public GoodsReceiptRequest(UUID ocId, List<ReceiptLine> lines) {
        this(ocId, lines, null);
    }

    public record ReceiptLine(
            @NotNull UUID productId,
            @NotNull UUID warehouseId,
            @NotNull @DecimalMin("0.001") BigDecimal receivedQty,
            @NotNull @DecimalMin("0") BigDecimal actualCost,
            @FutureOrPresent LocalDate expirationDate
    ) {}
}
