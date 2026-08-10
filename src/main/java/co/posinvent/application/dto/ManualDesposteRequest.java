package co.posinvent.application.dto;

import co.posinvent.domain.model.ManualDespostePlan.DesposteSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ManualDesposteRequest(
        @NotNull UUID sourceBatchId,
        @NotNull DesposteSourceType sourceType,
        @NotBlank @Size(max = 500) String manualJustification,
        @NotNull @DecimalMin("0") BigDecimal wasteWeight,
        @NotNull @DecimalMin("0") BigDecimal shrinkWeight,
        @Size(max = 500) String notes,
        @Valid @NotEmpty List<ManualDesposteCutRequest> cuts
) {
    public record ManualDesposteCutRequest(
            @NotNull UUID productId,
            @NotNull UUID warehouseId,
            @NotNull @DecimalMin("0.001") BigDecimal weight,
            @NotNull @DecimalMin("0.000001") BigDecimal suggestedSalePrice,
            @FutureOrPresent LocalDate expirationDate
    ) { }
}
