package co.posinvent.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TaxRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 100) String description,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
        @Size(max = 500) String observation,
        @Size(max = 2) String dianCode,
        @Size(max = 50) String providerExternalId,
        Integer sortOrder,
        Boolean active
) {}
