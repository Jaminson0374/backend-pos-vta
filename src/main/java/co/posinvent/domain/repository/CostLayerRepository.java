package co.posinvent.domain.repository;

import co.posinvent.domain.model.CostLayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CostLayerRepository {

    CostLayer save(CostLayer layer);

    List<CostLayer> findByProductBatchWarehouse(UUID productId, UUID batchId, UUID warehouseId);

    List<CostLayer> findByProductBatchWarehouseFefo(UUID productId, UUID batchId, UUID warehouseId);

    void deleteAllByProductBatchWarehouse(UUID productId, UUID batchId, UUID warehouseId);

    void deleteById(UUID id);
}
