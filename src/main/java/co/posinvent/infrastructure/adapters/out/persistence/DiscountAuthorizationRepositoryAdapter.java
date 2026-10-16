package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.DiscountAuthorization;
import co.posinvent.domain.repository.DiscountAuthorizationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class DiscountAuthorizationRepositoryAdapter implements DiscountAuthorizationRepository {

    private final DiscountAuthorizationJpaRepository jpa;
    private final DiscountAuthorizationMapper mapper;

    DiscountAuthorizationRepositoryAdapter(
            DiscountAuthorizationJpaRepository jpa, DiscountAuthorizationMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public DiscountAuthorization save(DiscountAuthorization authorization) {
        var entity = mapper.toEntity(authorization);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<DiscountAuthorization> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void markUsed(UUID id) {
        jpa.markUsedIfUnused(id);
    }
}
