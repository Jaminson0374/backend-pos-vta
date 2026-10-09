package co.posinvent.domain.repository;

import co.posinvent.domain.model.PucAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PucAccountRepository {
    List<PucAccount> findAllActive();
    List<PucAccount> findAll();
    List<PucAccount> findByAccountClass(int accountClass);
    List<PucAccount> searchByCodeOrName(String query);
    Optional<PucAccount> findById(UUID id);
    Optional<PucAccount> findByCode(String code);
    PucAccount save(PucAccount entity);
    boolean existsByCode(String code);
    long countProductsReferencing(UUID pucAccountId);
    List<PucAccount> findChildrenByCode(String code);
    long countChildrenByCode(String code);
    List<PucAccount> findAncestorsByCode(String code);
}
