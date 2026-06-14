package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DevolutionResponse(
        UUID creditNoteId,
        String documentNumber,
        BigDecimal totalAmount,
        int reversedItems,
        BigDecimal arAdjustment
) {}
