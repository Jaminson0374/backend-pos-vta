package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.DianResolution;
import co.posinvent.domain.repository.DianResolutionRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class DianResolutionRepositoryAdapter implements DianResolutionRepository {

    private final DianResolutionJpaRepository jpa;
    private final DianResolutionMapper mapper;

    DianResolutionRepositoryAdapter(DianResolutionJpaRepository jpa, DianResolutionMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DianResolution save(DianResolution domain) {
        var entity = domain.id() != null
                ? jpa.findById(domain.id()).orElse(new DianResolutionEntity())
                : new DianResolutionEntity();
        entity.setResolutionNumber(domain.resolutionNumber());
        entity.setResolutionDate(domain.resolutionDate());
        entity.setValidFrom(domain.validFrom());
        entity.setValidTo(domain.validTo());
        entity.setPrefix(domain.prefix());
        entity.setRangeFrom(domain.rangeFrom());
        entity.setRangeTo(domain.rangeTo());
        entity.setCurrentNumber(domain.currentNumber());
        entity.setSoftwarePin(domain.softwarePin());
        entity.setActive(domain.active());
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<DianResolution> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<DianResolution> findAll() {
        return jpa.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public Optional<DianResolution> findActive() {
        return jpa.findFirstByActiveTrueOrderByCreatedAtDesc().map(mapper::toDomain);
    }

    @Override
    public Optional<DianResolution> findActiveForUpdate() {
        return jpa.lockActive().stream().findFirst().map(mapper::toDomain);
    }

    @Override
    public void deactivateAll() {
        jpa.deactivateAll();
    }
}
