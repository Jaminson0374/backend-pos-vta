package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PurchaseReturnRequest(
        @NotNull UUID receiptId,
        @NotNull @NotEmpty @Valid List<LineItem> items,
        String reason
) {
    public record LineItem(
            @NotNull UUID productId,
            @NotNull UUID warehouseId,
            UUID batchId,
            @NotNull @DecimalMin("0.001") BigDecimal returnQty,
            @NotNull @DecimalMin("0") BigDecimal unitCost
    ) {}
}
