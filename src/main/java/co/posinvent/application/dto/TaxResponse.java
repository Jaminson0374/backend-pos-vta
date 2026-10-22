package co.posinvent.application.dto;

import co.posinvent.domain.model.Tax;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxResponse(
        UUID id,
        String code,
        String description,
        BigDecimal percentage,
        String observation,
        String dianCode,
        String providerExternalId,
        int sortOrder,
        boolean active
) {
    public static TaxResponse from(Tax t) {
        return new TaxResponse(
                t.id(), t.code(), t.description(), t.percentage(), t.observation(),
                t.dianCode(), t.providerExternalId(), t.sortOrder(), t.active());
    }
}
