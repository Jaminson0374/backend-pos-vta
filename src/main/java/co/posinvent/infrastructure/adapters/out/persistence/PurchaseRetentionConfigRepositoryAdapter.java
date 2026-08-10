package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseRetentionConfig;
import co.posinvent.domain.repository.PurchaseRetentionConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PurchaseRetentionConfigRepositoryAdapter implements PurchaseRetentionConfigRepository {

    private final PurchaseRetentionConfigJpaRepository jpa;
    private final PurchaseRetentionConfigMapper mapper;

    PurchaseRetentionConfigRepositoryAdapter(
            PurchaseRetentionConfigJpaRepository jpa,
            PurchaseRetentionConfigMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<PurchaseRetentionConfig> findAllActive() {
        return jpa.findByActiveTrueOrderBySortOrderAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PurchaseRetentionConfig> findAll() {
        return jpa.findAllByOrderBySortOrderAsc().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<PurchaseRetentionConfig> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseRetentionConfig> findByCode(String code) {
        return jpa.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public PurchaseRetentionConfig save(PurchaseRetentionConfig config) {
        return mapper.toDomain(jpa.save(mapper.toEntity(config)));
    }

    @Override
    public boolean existsByCode(String code) {
        return jpa.existsByCode(code);
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }
}
