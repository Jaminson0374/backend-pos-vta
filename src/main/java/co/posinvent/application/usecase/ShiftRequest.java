package co.posinvent.application.usecase;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ShiftRequest(
        @NotNull UUID cashRegisterId,
        UUID assignedTo,
        BigDecimal openingAmount
) {}
