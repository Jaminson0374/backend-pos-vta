package co.posinvent.application.port.in;

import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;

import java.math.BigDecimal;
import java.util.UUID;

public interface RecordMovementPort {

    InventoryMovement record(
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
    );
}
