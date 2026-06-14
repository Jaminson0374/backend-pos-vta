package co.posinvent.application.dto;

import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.PucAccount;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AccountingTemplateResponse(
    UUID id,
    String code,
    String name,
    String description,
    String module,
    boolean isDefault,
    boolean isActive,
    List<AccountingTemplateEntryResponse> entries,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AccountingTemplateResponse from(AccountingTemplate template, Map<UUID, PucAccount> accountMap) {
        var entryResponses = template.entries() == null ? List.<AccountingTemplateEntryResponse>of()
            : template.entries().stream()
                .map(e -> AccountingTemplateEntryResponse.from(e, accountMap.get(e.accountId())))
                .toList();
        return new AccountingTemplateResponse(
            template.id(),
            template.code(),
            template.name(),
            template.description(),
            template.module(),
            template.isDefault(),
            template.isActive(),
            entryResponses,
            template.createdAt(),
            template.updatedAt()
        );
    }

    /**
     * Convenience factory without account enrichment (account code/name will be null).
     */
    public static AccountingTemplateResponse from(AccountingTemplate template) {
        return from(template, Map.of());
    }
}
