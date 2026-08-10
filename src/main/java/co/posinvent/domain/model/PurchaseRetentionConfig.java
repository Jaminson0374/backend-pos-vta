package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseRetentionConfig(
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
) {}
