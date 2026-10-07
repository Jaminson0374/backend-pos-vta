package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CiiuActivity;
import co.posinvent.domain.repository.CiiuActivityRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
class CiiuActivityRepositoryAdapter implements CiiuActivityRepository {

    private final CiiuActivityJpaRepository jpa;
    private final CiiuActivityMapper mapper;

    CiiuActivityRepositoryAdapter(CiiuActivityJpaRepository jpa, CiiuActivityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public List<CiiuActivity> findAllActive() {
        return jpa.findByActiveTrueOrderByCodeAsc().stream()
                .map(mapper::toDomain)
                .toList();
    }
}
