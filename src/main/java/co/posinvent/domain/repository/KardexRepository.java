package co.posinvent.domain.repository;

import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.MovementType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KardexRepository {

    InventoryMovement save(InventoryMovement movement);

    /**
     * Finds every inventory movement tied to a business reference (e.g. all the
     * EXIT movements recorded for a sale, keyed by {@code "SALE"} + the source
     * document id). Used by the accounting layer to value COGS from the Kardex.
     */
    List<InventoryMovement> findByReference(String referenceType, UUID referenceId);

    Page<InventoryMovement> search(
            UUID productId,
            UUID batchId,
            UUID warehouseId,
            MovementType movementType,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    Optional<BigDecimal> getUnitCost(UUID productId, UUID warehouseId, String costingMethod);

    BigDecimal getCurrentStock(UUID productId, UUID warehouseId);
}
