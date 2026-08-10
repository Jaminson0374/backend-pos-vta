package co.posinvent.application.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PurchaseRetentionConfigRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rate,
        @NotNull @DecimalMin("0") BigDecimal baseMin,
        @Size(max = 50) String appliesToTaxRegime,
        @Size(max = 20) String appliesToPersonType,
        @Min(0) int sortOrder
) {}
