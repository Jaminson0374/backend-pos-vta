package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BatchAllocation(
        UUID batchId,
        BigDecimal quantity,
        BigDecimal unitCost
) {}
