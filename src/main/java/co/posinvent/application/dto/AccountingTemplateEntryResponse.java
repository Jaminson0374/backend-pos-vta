package co.posinvent.application.dto;

import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.PucAccount;

import java.util.UUID;

public record AccountingTemplateEntryResponse(
    UUID id,
    UUID templateId,
    String eventType,
    UUID accountId,
    String accountCode,
    String accountName,
    boolean isDebit,
    int priority
) {
    public static AccountingTemplateEntryResponse from(AccountingTemplateEntry entry, PucAccount account) {
        return new AccountingTemplateEntryResponse(
            entry.id(),
            entry.templateId(),
            entry.eventType(),
            entry.accountId(),
            account != null ? account.code() : null,
            account != null ? account.name() : null,
            entry.isDebit(),
            entry.priority()
        );
    }
}
