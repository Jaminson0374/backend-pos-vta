package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AccountingTemplate(
    UUID id,
    String code,
    String name,
    String description,
    String module,
    boolean isDefault,
    boolean isActive,
    List<AccountingTemplateEntry> entries,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
