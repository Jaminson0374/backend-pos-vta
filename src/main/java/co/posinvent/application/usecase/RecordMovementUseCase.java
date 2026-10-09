package co.posinvent.application.usecase;

import co.posinvent.application.port.in.RecordMovementPort;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.KardexRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RecordMovementUseCase implements RecordMovementPort {

    private final KardexRepository kardexRepo;

    public RecordMovementUseCase(KardexRepository kardexRepo) {
        this.kardexRepo = kardexRepo;
    }

    @Transactional
    public InventoryMovement record(
            UUID productId,
            UUID batchId,
            UUID warehouseId,
            MovementType type,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal previousQty,
            BigDecimal newQty,
            String referenceType,
            UUID referenceId,
            String notes
    ) {
        var movement = new InventoryMovement(
                null,
                productId,
                batchId,
                warehouseId,
                type,
                quantity,
                unitCost != null ? unitCost : BigDecimal.ZERO,
                previousQty,
                newQty,
                referenceType,
                referenceId,
                notes,
                "SYSTEM",
                null
        );
        return kardexRepo.save(movement);
    }
}
