package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptRequest(
        @NotNull UUID ocId,
        @NotNull @NotEmpty @Valid List<ReceiptLine> lines
) {
    public record ReceiptLine(
            @NotNull UUID productId,
            @NotNull UUID warehouseId,
            @NotNull @DecimalMin("0.001") BigDecimal receivedQty,
            @NotNull @DecimalMin("0") BigDecimal actualCost,
            @FutureOrPresent LocalDate expirationDate
    ) {}
}
