package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record Tax(
        UUID id,
        String code,
        String description,
        BigDecimal percentage,
        String observation,
        String dianCode,
        String siigoTaxId,
        int sortOrder,
        boolean active
) {
    public Tax {
        if (percentage == null) percentage = BigDecimal.ZERO;
    }
}
