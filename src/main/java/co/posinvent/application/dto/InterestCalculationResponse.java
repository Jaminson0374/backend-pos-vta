package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record InterestCalculationResponse(
        int processedCount,
        BigDecimal totalInterestCalculated,
        int skippedCount,
        List<String> errors
) {}
