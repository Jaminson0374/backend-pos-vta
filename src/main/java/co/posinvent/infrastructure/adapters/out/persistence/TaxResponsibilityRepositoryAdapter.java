package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.TaxResponsibility;
import co.posinvent.domain.repository.TaxResponsibilityRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class TaxResponsibilityRepositoryAdapter implements TaxResponsibilityRepository {

    private final TaxResponsibilityJpaRepository jpa;
    private final TaxResponsibilityMapper mapper;

    TaxResponsibilityRepositoryAdapter(TaxResponsibilityJpaRepository jpa, TaxResponsibilityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<TaxResponsibility> findAllActive() {
        return jpa.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
