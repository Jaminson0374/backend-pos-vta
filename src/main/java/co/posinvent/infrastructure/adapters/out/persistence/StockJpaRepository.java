package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface StockJpaRepository extends JpaRepository<InventoryStockEntity, UUID> {

    Optional<InventoryStockEntity> findByProductIdAndBatchIdAndWarehouseId(
            UUID productId, UUID batchId, UUID warehouseId);

    List<InventoryStockEntity> findByWarehouseId(UUID warehouseId);

    List<InventoryStockEntity> findByProductId(UUID productId);

    List<InventoryStockEntity> findByBatchId(UUID batchId);

    @Query(value = """
        SELECT is2.* FROM inventory_stock is2
        JOIN batches b ON b.id = is2.batch_id
        WHERE is2.product_id = :productId
          AND is2.warehouse_id = :warehouseId
          AND is2.current_quantity > 0
          AND b.status <> 'CLOSED'
        ORDER BY b.expiration_date ASC NULLS LAST, b.entry_date ASC
        """, nativeQuery = true)
    List<InventoryStockEntity> findAvailableByProductWarehouse(
            @Param("productId") UUID productId,
            @Param("warehouseId") UUID warehouseId);
}
