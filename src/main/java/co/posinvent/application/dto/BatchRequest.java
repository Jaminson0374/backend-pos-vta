package co.posinvent.application.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BatchRequest(
        @NotNull UUID supplierId,
        @NotNull UUID warehouseId,
        @NotNull UUID productId,
        @NotNull LocalDate entryDate,
        @NotNull @DecimalMin("0.001") BigDecimal initialWeight,
        @NotNull @DecimalMin("0")     BigDecimal purchaseCost,
        @Size(max = 500) String notes,
        UUID sourceReceiptId,
        UUID ocId,
        @FutureOrPresent LocalDate expirationDate,
        String batchType,
        UUID unitOfMeasureId
) {}