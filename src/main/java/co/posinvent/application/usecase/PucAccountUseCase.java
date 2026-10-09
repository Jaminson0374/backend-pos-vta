package co.posinvent.application.usecase;

import co.posinvent.application.dto.PucAccountRequest;
import co.posinvent.application.dto.PucAccountResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.repository.PucAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PucAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(PucAccountUseCase.class);

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

        // Validate parent hierarchy and class coherence
        validateParentRules(request.parentCode(), request.level(), request.accountClass(), null);

        // Validate allowsTransactions only for level >= 4
        if (request.allowsTransactions() && request.level() < 4) {
            throw new BusinessException("INVALID_TRANSACTION_LEVEL",
                "Las cuentas de nivel 1, 2 o 3 no pueden recibir movimientos directos.");
        }

        // Warn on class/nature mismatch
        warnNatureClassMismatch(request.accountClass(), request.accountNature());

        var entity = new PucAccount(
            null,
            request.code(),
            request.name(),
            request.level(),
            request.parentCode(),
            request.accountClass(),
            request.accountNature(),
            request.allowsTransactions(),
            request.active(),
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

        // Validate parent hierarchy and class coherence
        validateParentRules(request.parentCode(), request.level(), request.accountClass(), existing.code());

        // Validate no self-reference
        if (request.parentCode() != null && request.parentCode().equals(existing.code())) {
            throw new BusinessException("SELF_REFERENCE", "Una cuenta no puede ser padre de sí misma.");
        }

        // Validate no cycles: updated account must not be an ancestor of the new parent
        if (request.parentCode() != null && !request.parentCode().isBlank()) {
            var ancestors = repository.findAncestorsByCode(request.parentCode());
            boolean wouldCycle = ancestors.stream().anyMatch(a -> a.code().equals(existing.code()));
            if (wouldCycle) {
                throw new BusinessException("HIERARCHY_CYCLE",
                    "La cuenta padre seleccionada crearía un ciclo jerárquico.");
            }
        }

        // Validate allowsTransactions only for level >= 4
        if (request.allowsTransactions() && request.level() < 4) {
            throw new BusinessException("INVALID_TRANSACTION_LEVEL",
                "Las cuentas de nivel 1, 2 o 3 no pueden recibir movimientos directos.");
        }

        // Warn on class/nature mismatch
        warnNatureClassMismatch(request.accountClass(), request.accountNature());

        var updated = new PucAccount(
            existing.id(),
            request.code(),
            request.name(),
            request.level(),
            request.parentCode(),
            request.accountClass(),
            request.accountNature(),
            request.allowsTransactions(),
            request.active(),
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

        // Validate no active children before deactivation
        long childrenCount = repository.countChildrenByCode(existing.code());
        if (childrenCount > 0) {
            throw new BusinessException("HAS_ACTIVE_CHILDREN",
                "No se puede desactivar: la cuenta tiene " + childrenCount + " cuenta(s) hija(s). Desactívelas primero.");
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

    @Transactional(readOnly = true)
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Validates that parentCode exists, has the correct level, and matches the account class.
     */
    private void validateParentRules(String parentCode, int level, int accountClass, String selfCode) {
        boolean hasParent = parentCode != null && !parentCode.isBlank();

        if (level == 1) {
            if (hasParent) {
                throw new BusinessException("LEVEL1_NO_PARENT",
                    "Las cuentas de nivel 1 no tienen cuenta padre.");
            }
            return;
        }

        // Level > 1 requires a parent
        if (!hasParent) {
            throw new BusinessException("PARENT_REQUIRED",
                "El nivel " + level + " requiere una cuenta padre de nivel " + (level - 1) + ".");
        }

        // Parent must exist
        var parent = repository.findByCode(parentCode)
            .orElseThrow(() -> new BusinessException("INVALID_PARENT",
                "La cuenta padre '" + parentCode + "' no existe."));

        // Parent level must be exactly level - 1
        if (parent.level() != level - 1) {
            throw new BusinessException("PARENT_LEVEL_MISMATCH",
                "La cuenta padre '" + parentCode + "' es nivel " + parent.level()
                + ", pero se requiere nivel " + (level - 1)
                + " para una cuenta de nivel " + level + ".");
        }

        // Class must match parent's class
        if (parent.accountClass() != accountClass) {
            throw new BusinessException("CLASS_MISMATCH",
                "La clase contable (" + accountClass + ") no coincide con la clase del padre '"
                + parentCode + "' (" + parent.accountClass() + ").");
        }
    }

    /**
     * Logs a warning when accountClass and accountNature are atypical per standard accounting conventions.
     * Does NOT reject the request — this is advisory only.
     */
    private void warnNatureClassMismatch(int accountClass, String nature) {
        boolean typicalDebit = (accountClass == 1 || accountClass == 5 || accountClass == 6
                             || accountClass == 7 || accountClass == 8);
        boolean typicalCredit = (accountClass == 2 || accountClass == 3 || accountClass == 4
                              || accountClass == 9);

        if ((typicalDebit && "CREDITO".equals(nature)) || (typicalCredit && "DEBITO".equals(nature))) {
            log.warn("Posible inconsistencia: clase={} naturaleza={} (no es la combinación típica)",
                accountClass, nature);
        }
    }
}
