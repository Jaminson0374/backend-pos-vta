package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AccountingTemplateRequest(
    @NotBlank @Size(max = 30) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 500) String description,
    @NotBlank String module,
    boolean isDefault,
    boolean isActive,
    @NotEmpty @Valid List<AccountingTemplateEntryRequest> entries
) {}
