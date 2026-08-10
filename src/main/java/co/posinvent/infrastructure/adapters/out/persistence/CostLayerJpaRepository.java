package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CostLayerJpaRepository extends JpaRepository<CostLayerEntity, UUID> {

    List<CostLayerEntity> findByProductIdAndBatchIdAndWarehouseIdOrderByEntryDateAsc(
            UUID productId, UUID batchId, UUID warehouseId);

    @Query(value = """
            SELECT cl.* FROM cost_layers cl
            JOIN batches b ON b.id = cl.batch_id
            WHERE cl.product_id = :productId
              AND cl.batch_id = :batchId
              AND cl.warehouse_id = :warehouseId
              AND cl.remaining_quantity > 0
            ORDER BY b.expiration_date ASC NULLS LAST, cl.entry_date ASC
            """, nativeQuery = true)
    List<CostLayerEntity> findByProductBatchWarehouseFefo(
            @Param("productId") UUID productId,
            @Param("batchId") UUID batchId,
            @Param("warehouseId") UUID warehouseId);

    @Modifying
    @Query("DELETE FROM CostLayerEntity c WHERE c.productId = :productId AND c.batchId = :batchId AND c.warehouseId = :warehouseId")
    void deleteAllByProductBatchWarehouse(
            @Param("productId") UUID productId,
            @Param("batchId") UUID batchId,
            @Param("warehouseId") UUID warehouseId);
}
