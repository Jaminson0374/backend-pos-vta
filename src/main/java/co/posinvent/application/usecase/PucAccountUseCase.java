package co.posinvent.application.usecase;

import co.posinvent.application.dto.PucAccountRequest;
import co.posinvent.application.dto.PucAccountResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.repository.PucAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PucAccountUseCase {

    private final PucAccountRepository repository;

    public PucAccountUseCase(PucAccountRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PucAccountResponse> listAll() {
        return repository.findAllActive().stream().map(PucAccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PucAccountResponse> listByClass(int accountClass) {
        return repository.findByAccountClass(accountClass).stream().map(PucAccountResponse::from).toList();
    }

    @Transactional
    public PucAccountResponse create(PucAccountRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new BusinessException("DUPLICATE_CODE", "Ya existe una cuenta PUC con ese código.");
        }
        var entity = new PucAccount(
            null,
            request.code(),
            request.name(),
            request.level(),
            request.parentCode(),
            request.accountClass(),
            request.accountNature(),
            request.allowsTransactions(),
            true,
            null,
            null
        );
        return PucAccountResponse.from(repository.save(entity));
    }

    @Transactional
    public PucAccountResponse update(UUID id, PucAccountRequest request) {
        var existing = repository.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Cuenta PUC no encontrada."));

        if (!existing.code().equals(request.code()) && repository.existsByCode(request.code())) {
            throw new BusinessException("DUPLICATE_CODE", "Ya existe una cuenta PUC con ese código.");
        }

        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            repository.findByCode(request.parentCode())
                .orElseThrow(() -> new BusinessException("INVALID_PARENT", "La cuenta padre no existe."));
        }

        var updated = new PucAccount(
            existing.id(),
            request.code(),
            request.name(),
            request.level(),
            request.parentCode(),
            request.accountClass(),
            request.accountNature(),
            request.allowsTransactions(),
            existing.active(),
            existing.createdAt(),
            existing.updatedAt()
        );
        return PucAccountResponse.from(repository.save(updated));
    }

    @Transactional
    public void deactivate(UUID id) {
        var existing = repository.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Cuenta PUC no encontrada."));

        if (!existing.active()) {
            throw new BusinessException("ALREADY_INACTIVE", "La cuenta ya está inactiva.");
        }

        long refCount = repository.countProductsReferencing(id);
        if (refCount > 0) {
            throw new BusinessException("REFERENCED_BY_PRODUCTS",
                "No se puede desactivar: la cuenta está referenciada por " + refCount + " producto(s).");
        }

        var deactivated = new PucAccount(
            existing.id(),
            existing.code(),
            existing.name(),
            existing.level(),
            existing.parentCode(),
            existing.accountClass(),
            existing.accountNature(),
            existing.allowsTransactions(),
            false,
            existing.createdAt(),
            existing.updatedAt()
        );
        repository.save(deactivated);
    }

    @Transactional(readOnly = true)
    public PucAccountResponse getById(UUID id) {
        return repository.findById(id)
            .map(PucAccountResponse::from)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Cuenta PUC no encontrada."));
    }

    @Transactional(readOnly = true)
    public List<PucAccountResponse> tree(String search) {
        List<PucAccount> accounts;
        if (search != null && !search.isBlank()) {
            accounts = repository.searchByCodeOrName(search.trim());
        } else {
            accounts = repository.findAll();
        }
        return accounts.stream().map(PucAccountResponse::from).toList();
    }
}
