package co.posinvent.domain.repository;

import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository {

    Shift save(Shift shift);

    Optional<Shift> findById(UUID id);

    Optional<Shift> findByCashRegisterIdAndStatus(UUID cashRegisterId, ShiftStatus status);

    Page<Shift> findAll(Pageable pageable);

    Page<Shift> findByUserIdOrAssignedTo(UUID userId, UUID assignedTo, Pageable pageable);
}
