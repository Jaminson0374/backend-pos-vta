package co.posinvent.domain.repository;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchRepository {

    Batch save(Batch batch);

    Optional<Batch> findById(UUID id);

    Page<Batch> findAll(Pageable pageable);

    Page<Batch> findByStatus(BatchStatus status, Pageable pageable);

    Page<Batch> findByWarehouse(UUID warehouseId, Pageable pageable);

    List<Batch> findBySourceReceiptId(UUID sourceReceiptId);

    /**
     * Returns a page of batches enriched with product, supplier, and warehouse names
     * via LEFT JOINs to handle nullable FK references gracefully.
     */
    Page<Batch> findAllWithNames(Pageable pageable);

    /**
     * Returns a page of batches filtered by status, enriched with LEFT JOIN names.
     */
    Page<Batch> findByStatusWithNames(BatchStatus status, Pageable pageable);

    /**
     * Returns a single batch enriched with product, supplier, and warehouse names.
     */
    Optional<Batch> findByIdWithNames(UUID id);

    /**
     * Returns all child batches for a given parent batch, enriched with names.
     */
    List<Batch> findByParentBatchIdWithNames(UUID parentBatchId);
}
