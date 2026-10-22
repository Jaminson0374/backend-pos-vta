package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Tax;
import co.posinvent.domain.repository.TaxRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class TaxRepositoryAdapter implements TaxRepository {

    private final TaxJpaRepository jpa;
    private final TaxMapper mapper;

    TaxRepositoryAdapter(TaxJpaRepository jpa, TaxMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<Tax> findAll() {
        return jpa.findAllByOrderBySortOrderAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Tax> findAllActive() {
        return jpa.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Tax> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Tax> findByCode(String code) {
        return jpa.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public Tax save(Tax tax) {
        var entity = tax.id() == null
                ? mapper.toEntity(tax)
                : jpa.findById(tax.id()).orElseGet(() -> mapper.toEntity(tax));
        entity.setCode(tax.code());
        entity.setDescription(tax.description());
        entity.setPercentage(tax.percentage());
        entity.setObservation(tax.observation());
        entity.setDianCode(tax.dianCode());
        entity.setProviderExternalId(tax.providerExternalId());
        entity.setSortOrder(tax.sortOrder());
        entity.setActive(tax.active());
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsByCode(code);
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, UUID id) {
        return jpa.existsByCodeAndIdNot(code, id);
    }
}
