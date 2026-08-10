package co.posinvent.domain.repository;

import co.posinvent.domain.model.InventoryStock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockRepository {

    InventoryStock save(InventoryStock stock);

    Optional<InventoryStock> findByProductBatchWarehouse(UUID productId, UUID batchId, UUID warehouseId);

    List<InventoryStock> findByWarehouse(UUID warehouseId);

    List<InventoryStock> findByProduct(UUID productId);

    List<InventoryStock> findByBatch(UUID batchId);

    List<InventoryStock> findAvailableByProductWarehouse(UUID productId, UUID warehouseId);
}
