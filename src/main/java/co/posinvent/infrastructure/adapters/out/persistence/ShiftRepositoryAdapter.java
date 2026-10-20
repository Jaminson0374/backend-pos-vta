package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.repository.ShiftRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class ShiftRepositoryAdapter implements ShiftRepository {

    private final ShiftJpaRepository jpa;
    private final ShiftMapper mapper;

    ShiftRepositoryAdapter(ShiftJpaRepository jpa, ShiftMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Shift save(Shift shift) {
        var entity = mapper.toEntity(shift);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Shift> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Shift> findByCashRegisterIdAndStatus(UUID cashRegisterId, ShiftStatus status) {
        return jpa.findByCashRegisterIdAndStatus(cashRegisterId, status).map(mapper::toDomain);
    }

    @Override
    public Page<Shift> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Shift> findByUserIdOrAssignedTo(UUID userId, UUID assignedTo, Pageable pageable) {
        return jpa.findByUserIdOrAssignedTo(userId, assignedTo, pageable).map(mapper::toDomain);
    }
}
