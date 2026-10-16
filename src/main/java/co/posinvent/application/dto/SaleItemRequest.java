package co.posinvent.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.001") BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotNull String taxType,
        UUID batchId,
        String discountType,
        BigDecimal discountValue
) {

    public SaleItemRequest {
        if (discountType == null || discountType.isBlank()) discountType = "NONE";
        if (discountValue == null) discountValue = BigDecimal.ZERO;
    }

    /** Backwards-compatible constructor for lines without a manual discount. */
    public SaleItemRequest(
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String taxType,
            UUID batchId
    ) {
        this(productId, quantity, unitPrice, taxType, batchId, "NONE", BigDecimal.ZERO);
    }
}
