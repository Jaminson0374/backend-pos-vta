package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.FiscalResponsibility;
import co.posinvent.domain.repository.FiscalResponsibilityRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class FiscalResponsibilityRepositoryAdapter implements FiscalResponsibilityRepository {

    private final FiscalResponsibilityJpaRepository jpa;
    private final FiscalResponsibilityMapper mapper;

    FiscalResponsibilityRepositoryAdapter(FiscalResponsibilityJpaRepository jpa, FiscalResponsibilityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<FiscalResponsibility> findAllActive() {
        return jpa.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
