package co.posinvent.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AccountingTemplateEntryRequest(
    @NotBlank String eventType,
    @NotNull UUID accountId,
    boolean isDebit,
    int priority
) {}
