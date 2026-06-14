package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductGroup(
    UUID id,
    String name,
    UUID categoryId,
    boolean active,
    OffsetDateTime createdAt,
    UUID accountingTemplateId
) {}
