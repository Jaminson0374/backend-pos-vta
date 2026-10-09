package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Tax;
import co.posinvent.domain.repository.TaxRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class TaxRepositoryAdapter implements TaxRepository {

    private final TaxJpaRepository jpa;
    private final TaxMapper mapper;

    TaxRepositoryAdapter(TaxJpaRepository jpa, TaxMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<Tax> findAllActive() {
        return jpa.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
