package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Batch.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BatchJpaRepository extends JpaRepository<BatchEntity, UUID> {

    Page<BatchEntity> findByStatus(BatchStatus status, Pageable pageable);

    Page<BatchEntity> findByWarehouseId(UUID warehouseId, Pageable pageable);

    List<BatchEntity> findBySourceReceiptId(UUID sourceReceiptId);

    @Query(value = """
        SELECT b.id,
               b.supplier_id        AS supplierId,
               b.warehouse_id       AS warehouseId,
               b.product_id         AS productId,
               b.entry_date         AS entryDate,
               b.initial_weight     AS initialWeight,
               b.purchase_cost      AS purchaseCost,
               b.status,
               b.notes,
               b.expiration_date    AS expirationDate,
               b.created_by         AS createdBy,
               b.created_at         AS createdAt,
               b.source_receipt_id  AS sourceReceiptId,
               b.oc_id              AS ocId,
               b.parent_batch_id    AS parentBatchId,
               b.batch_type         AS batchType,
               b.unit_of_measure_id AS unitOfMeasureId,
               p.name               AS productName,
               tp.name              AS supplierName,
               w.name               AS warehouseName
        FROM batches b
        LEFT JOIN products     p  ON b.product_id  = p.id
        LEFT JOIN third_parties tp ON b.supplier_id = tp.id
        LEFT JOIN warehouses   w  ON b.warehouse_id = w.id
        """,
        countQuery = "SELECT count(*) FROM batches",
        nativeQuery = true)
    Page<BatchWithNamesProjection> findAllWithNames(Pageable pageable);

    @Query(value = """
        SELECT b.id,
               b.supplier_id        AS supplierId,
               b.warehouse_id       AS warehouseId,
               b.product_id         AS productId,
               b.entry_date         AS entryDate,
               b.initial_weight     AS initialWeight,
               b.purchase_cost      AS purchaseCost,
               b.status,
               b.notes,
               b.expiration_date    AS expirationDate,
               b.created_by         AS createdBy,
               b.created_at         AS createdAt,
               b.source_receipt_id  AS sourceReceiptId,
               b.oc_id              AS ocId,
               b.parent_batch_id    AS parentBatchId,
               b.batch_type         AS batchType,
               b.unit_of_measure_id AS unitOfMeasureId,
               p.name               AS productName,
               tp.name              AS supplierName,
               w.name               AS warehouseName
        FROM batches b
        LEFT JOIN products     p  ON b.product_id  = p.id
        LEFT JOIN third_parties tp ON b.supplier_id = tp.id
        LEFT JOIN warehouses   w  ON b.warehouse_id = w.id
        WHERE b.id = :id
        """,
        nativeQuery = true)
    Optional<BatchWithNamesProjection> findByIdWithNames(@Param("id") UUID id);

    @Query(value = "SELECT * FROM batches WHERE parent_batch_id = :parentBatchId",
            nativeQuery = true)
    List<BatchEntity> findByParentBatchId(@Param("parentBatchId") UUID parentBatchId);

    @Query(value = """
        SELECT b.id,
               b.supplier_id        AS supplierId,
               b.warehouse_id       AS warehouseId,
               b.product_id         AS productId,
               b.entry_date         AS entryDate,
               b.initial_weight     AS initialWeight,
               b.purchase_cost      AS purchaseCost,
               b.status,
               b.notes,
               b.expiration_date    AS expirationDate,
               b.created_by         AS createdBy,
               b.created_at         AS createdAt,
               b.source_receipt_id  AS sourceReceiptId,
               b.oc_id              AS ocId,
               b.parent_batch_id    AS parentBatchId,
               b.batch_type         AS batchType,
               b.unit_of_measure_id AS unitOfMeasureId,
               p.name               AS productName,
               tp.name              AS supplierName,
               w.name               AS warehouseName
        FROM batches b
        LEFT JOIN products     p  ON b.product_id  = p.id
        LEFT JOIN third_parties tp ON b.supplier_id = tp.id
        LEFT JOIN warehouses   w  ON b.warehouse_id = w.id
        WHERE CAST(b.status AS text) = CAST(:status AS text)
        """,
        countQuery = "SELECT count(*) FROM batches WHERE CAST(status AS text) = CAST(:status AS text)",
        nativeQuery = true)
    Page<BatchWithNamesProjection> findByStatusWithNames(@Param("status") BatchStatus status, Pageable pageable);

    @Query(value = """
        SELECT b.id,
               b.supplier_id        AS supplierId,
               b.warehouse_id       AS warehouseId,
               b.product_id         AS productId,
               b.entry_date         AS entryDate,
               b.initial_weight     AS initialWeight,
               b.purchase_cost      AS purchaseCost,
               b.status,
               b.notes,
               b.expiration_date    AS expirationDate,
               b.created_by         AS createdBy,
               b.created_at         AS createdAt,
               b.source_receipt_id  AS sourceReceiptId,
               b.oc_id              AS ocId,
               b.parent_batch_id    AS parentBatchId,
               b.batch_type         AS batchType,
               b.unit_of_measure_id AS unitOfMeasureId,
               p.name               AS productName,
               tp.name              AS supplierName,
               w.name               AS warehouseName
        FROM batches b
        LEFT JOIN products     p  ON b.product_id  = p.id
        LEFT JOIN third_parties tp ON b.supplier_id = tp.id
        LEFT JOIN warehouses   w  ON b.warehouse_id = w.id
        WHERE b.parent_batch_id = :parentBatchId
        """,
        nativeQuery = true)
    List<BatchWithNamesProjection> findByParentBatchIdWithNames(@Param("parentBatchId") UUID parentBatchId);
}
