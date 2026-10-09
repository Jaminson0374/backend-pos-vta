package co.posinvent.application.usecase;

import co.posinvent.application.dto.InventoryMovementResponse;
import co.posinvent.application.port.in.KardexQueryPort;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.KardexRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class KardexQueryUseCase implements KardexQueryPort {

    private final KardexRepository kardexRepo;

    public KardexQueryUseCase(KardexRepository kardexRepo) {
        this.kardexRepo = kardexRepo;
    }

    public Page<InventoryMovementResponse> search(
            UUID productId,
            UUID batchId,
            UUID warehouseId,
            String movementType,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        MovementType type = movementType != null && !movementType.isBlank()
                ? MovementType.valueOf(movementType.toUpperCase())
                : null;

        return kardexRepo.search(productId, batchId, warehouseId, type, from, to, pageable)
                .map(InventoryMovementResponse::from);
    }
}
