package co.posinvent.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProduceRequest(
        @NotNull UUID formulaProductId,
        @NotNull UUID warehouseId,
        @NotNull @DecimalMin("0.0001") BigDecimal quantity,
        @DecimalMin("0") BigDecimal laborCost,
        BigDecimal overheadCost,
        String notes,
        @FutureOrPresent LocalDate expirationDate
) {}
