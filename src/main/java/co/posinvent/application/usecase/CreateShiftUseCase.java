package co.posinvent.application.usecase;

import co.posinvent.domain.model.Shift;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.model.User;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.domain.repository.UserRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CreateShiftUseCase {

    private static final String ROLE_CASHIER = "CAJERO";

    private final ShiftRepository shiftRepository;
    private final CurrentActorProvider actorProvider;
    private final UserRepository userRepository;

    @Autowired
    public CreateShiftUseCase(
            ShiftRepository shiftRepository,
            CurrentActorProvider actorProvider,
            UserRepository userRepository
    ) {
        this.shiftRepository = shiftRepository;
        this.actorProvider = actorProvider;
        this.userRepository = userRepository;
    }

    @Transactional
    public ShiftResponse execute(ShiftRequest request) {
        var actor = actorProvider.current();
        UUID assignedTo = request.assignedTo();

        if (assignedTo != null && !assignedTo.equals(actor.userId())) {
            if (!actor.isAdmin()) {
                throw new AccessDeniedException(
                        "Solo un administrador puede asignar el turno a otro usuario.");
            }
            validateAssignableUser(assignedTo);
        }

        // Validate: no existing OPEN shift for same cash register
        var existing = shiftRepository.findByCashRegisterIdAndStatus(request.cashRegisterId(), ShiftStatus.OPEN);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un turno ABIERTO para la caja " + request.cashRegisterId());
        }

        var openingAmount = request.openingAmount() != null ? request.openingAmount() : BigDecimal.ZERO;

        var shift = shiftRepository.save(new Shift(
                null,
                request.cashRegisterId(),
                actor.userId(),
                assignedTo,
                null,
                null,
                null,
                openingAmount,
                null,
                ShiftStatus.OPEN,
                null,
                null
        ));

        return ShiftResponse.from(shift);
    }

    private void validateAssignableUser(UUID assignedTo) {
        User target = userRepository.findById(assignedTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El usuario asignado no existe."));

        if (!target.isActive()) {
            throw new IllegalArgumentException("El usuario asignado está inactivo.");
        }

        if (target.role() == null || !ROLE_CASHIER.equalsIgnoreCase(target.role().name())) {
            throw new IllegalArgumentException("El usuario asignado debe tener rol CAJERO.");
        }
    }
}
