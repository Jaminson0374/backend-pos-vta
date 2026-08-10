package co.posinvent.application.dto;

import co.posinvent.domain.model.PurchaseRetentionConfig;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseRetentionConfigResponse(
        UUID id,
        String code,
        String name,
        String description,
        BigDecimal rate,
        BigDecimal baseMin,
        String appliesToTaxRegime,
        String appliesToPersonType,
        boolean active,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PurchaseRetentionConfigResponse from(PurchaseRetentionConfig c) {
        return new PurchaseRetentionConfigResponse(
                c.id(), c.code(), c.name(), c.description(),
                c.rate(), c.baseMin(), c.appliesToTaxRegime(), c.appliesToPersonType(),
                c.active(), c.sortOrder(), c.createdAt(), c.updatedAt()
        );
    }
}
