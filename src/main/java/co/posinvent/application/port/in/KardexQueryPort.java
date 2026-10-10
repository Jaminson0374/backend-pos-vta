package co.posinvent.application.port.in;

import co.posinvent.application.dto.InventoryMovementResponse;
import org.springframework.data.domain.Page;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface KardexQueryPort {

    Page<InventoryMovementResponse> search(
            UUID productId,
            UUID batchId,
            UUID warehouseId,
            String movementType,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    );
}
