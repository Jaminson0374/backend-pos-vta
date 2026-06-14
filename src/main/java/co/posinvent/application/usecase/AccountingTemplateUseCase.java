package co.posinvent.application.usecase;

import co.posinvent.application.dto.AccountingTemplateEntryRequest;
import co.posinvent.application.dto.AccountingTemplateRequest;
import co.posinvent.application.dto.AccountingTemplateResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.AccountingTemplate;
import co.posinvent.domain.model.AccountingTemplateEntry;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.repository.AccountingTemplateRepository;
import co.posinvent.domain.repository.PucAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AccountingTemplateUseCase {

    private final AccountingTemplateRepository templateRepository;
    private final PucAccountRepository pucAccountRepository;

    public AccountingTemplateUseCase(
            AccountingTemplateRepository templateRepository,
            PucAccountRepository pucAccountRepository) {
        this.templateRepository = templateRepository;
        this.pucAccountRepository = pucAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountingTemplateResponse> list(String module) {
        var templates = templateRepository.findAll();
        if (module != null && !module.isBlank()) {
            templates = templates.stream()
                    .filter(t -> t.module().equalsIgnoreCase(module))
                    .toList();
        }
        return templates.stream().map(AccountingTemplateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountingTemplateResponse getById(UUID id) {
        var template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla contable", id));
        return enrichWithAccounts(template);
    }

    @Transactional
    public AccountingTemplateResponse create(AccountingTemplateRequest request) {
        validateRequest(request, null);

        var template = new AccountingTemplate(
                null,
                request.code(),
                request.name(),
                request.description(),
                request.module(),
                request.isDefault(),
                request.isActive(),
                buildEntries(request.entries(), null),
                null,
                null
        );

        var saved = templateRepository.save(template);
        return enrichWithAccounts(saved);
    }

    @Transactional
    public AccountingTemplateResponse update(UUID id, AccountingTemplateRequest request) {
        var existing = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla contable", id));

        validateRequest(request, id);

        var updated = new AccountingTemplate(
                existing.id(),
                request.code(),
                request.name(),
                request.description(),
                request.module(),
                request.isDefault(),
                request.isActive(),
                buildEntries(request.entries(), existing.id()),
                existing.createdAt(),
                null
        );

        var saved = templateRepository.save(updated);
        return enrichWithAccounts(saved);
    }

    @Transactional
    public void delete(UUID id) {
        templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla contable", id));
        templateRepository.deleteById(id);
    }

    // ─── Validation helpers ───

    private void validateRequest(AccountingTemplateRequest request, UUID excludeId) {
        // Check duplicate code
        var existingByCode = templateRepository.findByCode(request.code());
        if (existingByCode.isPresent() &&
                (excludeId == null || !existingByCode.get().id().equals(excludeId))) {
            throw new BusinessException("DUPLICATE_CODE",
                    "Ya existe una plantilla con el código: " + request.code());
        }

        // Check duplicate (event_type, account_id) combos in entries
        // Same event_type with DIFFERENT account_id is now allowed (e.g., per-rate SALE_TAX)
        var seenKeys = new HashSet<String>();
        for (var entry : request.entries()) {
            var key = entry.eventType().toUpperCase() + ":" + entry.accountId();
            if (!seenKeys.add(key)) {
                throw new BusinessException("DUPLICATE_EVENT_TYPE",
                        "Entrada duplicada: event_type '" + entry.eventType()
                                + "' con account_id '" + entry.accountId() + "'.");
            }
        }

        // Validate all accountIds exist
        for (var entry : request.entries()) {
            pucAccountRepository.findById(entry.accountId())
                    .orElseThrow(() -> new BusinessException("INVALID_ACCOUNT",
                            "La cuenta PUC " + entry.accountId() + " no existe."));
        }
    }

    // ─── Builder helpers ───

    private List<AccountingTemplateEntry> buildEntries(List<AccountingTemplateEntryRequest> requests, UUID templateId) {
        if (requests == null) return List.of();
        return requests.stream()
                .map(r -> new AccountingTemplateEntry(
                        null,
                        templateId,
                        r.eventType().toUpperCase(),
                        r.accountId(),
                        r.isDebit(),
                        r.priority()
                ))
                .toList();
    }

    // ─── Enrichment ───

    private AccountingTemplateResponse enrichWithAccounts(AccountingTemplate template) {
        if (template.entries() == null || template.entries().isEmpty()) {
            return AccountingTemplateResponse.from(template);
        }

        Set<UUID> accountIds = template.entries().stream()
                .map(AccountingTemplateEntry::accountId)
                .collect(Collectors.toSet());

        Map<UUID, PucAccount> accountMap = new HashMap<>();
        for (UUID accountId : accountIds) {
            pucAccountRepository.findById(accountId).ifPresent(a -> accountMap.put(accountId, a));
        }

        return AccountingTemplateResponse.from(template, accountMap);
    }
}
