package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.CashCount;
import co.posinvent.domain.repository.CashCountRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class CashCountRepositoryAdapter implements CashCountRepository {

    private final CashCountJpaRepository jpa;
    private final CashCountMapper mapper;

    CashCountRepositoryAdapter(CashCountJpaRepository jpa, CashCountMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public CashCount save(CashCount cashCount) {
        var entity = mapper.toEntity(cashCount);
        if (entity.getLines() != null) {
            entity.getLines().forEach(line -> line.setCashCount(entity));
        }
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<CashCount> findByShiftId(UUID shiftId) {
        return jpa.findByShiftIdOrderByCreatedAtDesc(shiftId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
