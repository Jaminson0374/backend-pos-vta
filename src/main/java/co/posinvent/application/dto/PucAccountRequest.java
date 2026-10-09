package co.posinvent.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PucAccountRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 200) String name,
    @Min(1) @Max(5) int level,
    @Size(max = 20) String parentCode,
    @Min(1) @Max(9) int accountClass,
    @NotBlank @Pattern(regexp = "DEBITO|CREDITO") String accountNature,
    boolean allowsTransactions,
    boolean active
) {}
