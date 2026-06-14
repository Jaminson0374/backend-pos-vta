package co.posinvent.domain.model;

import java.util.UUID;

public record AccountingTemplateEntry(
    UUID id,
    UUID templateId,
    String eventType,
    UUID accountId,
    boolean isDebit,
    int priority
) {}
