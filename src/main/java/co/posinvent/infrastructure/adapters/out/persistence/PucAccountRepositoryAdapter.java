package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PucAccount;
import co.posinvent.domain.repository.PucAccountRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PucAccountRepositoryAdapter implements PucAccountRepository {

    private final PucAccountJpaRepository jpa;
    private final ProductJpaRepository productJpa;
    private final PucAccountMapper mapper;

    PucAccountRepositoryAdapter(PucAccountJpaRepository jpa, ProductJpaRepository productJpa, PucAccountMapper mapper) {
        this.jpa = jpa;
        this.productJpa = productJpa;
        this.mapper = mapper;
    }

    @Override
    public List<PucAccount> findAllActive() {
        return jpa.findByActiveTrueOrderByCodeAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PucAccount> findAll() {
        return jpa.findAllByOrderByCodeAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PucAccount> findByAccountClass(int accountClass) {
        return jpa.findByAccountClassAndActiveTrue((short) accountClass).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PucAccount> searchByCodeOrName(String query) {
        return jpa.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(
            query, query).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<PucAccount> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PucAccount> findByCode(String code) {
        return jpa.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public PucAccount save(PucAccount domain) {
        return mapper.toDomain(jpa.save(mapper.toEntity(domain)));
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsByCode(code);
    }

    @Override
    public long countProductsReferencing(UUID pucAccountId) {
        return productJpa.countByPucAccountId(pucAccountId);
    }

    @Override
    public List<PucAccount> findChildrenByCode(String code) {
        return jpa.findByParentCode(code).stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countChildrenByCode(String code) {
        return jpa.countByParentCode(code);
    }

    @Override
    public List<PucAccount> findAncestorsByCode(String code) {
        // Recursively walk up the parent chain to build the ancestor list
        java.util.List<PucAccount> ancestors = new java.util.ArrayList<>();
        String currentCode = code;
        int safety = 0;
        while (currentCode != null && !currentCode.isBlank() && safety < 10) {
            final String lookup = currentCode;
            var parent = jpa.findByCode(lookup);
            if (parent.isPresent()) {
                var domain = mapper.toDomain(parent.get());
                ancestors.add(0, domain); // prepend to maintain root-first order
                currentCode = domain.parentCode();
            } else {
                break;
            }
            safety++;
        }
        return ancestors;
    }
}
