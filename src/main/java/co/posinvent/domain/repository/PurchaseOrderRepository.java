package co.posinvent.domain.repository;

import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository {

    PurchaseOrder save(PurchaseOrder purchaseOrder);

    Optional<PurchaseOrder> findById(UUID id);

    /**
     * Loads a purchase order acquiring a PESSIMISTIC_WRITE lock on its row, so concurrent
     * goods receipts against the same OC are serialized and the pending-quantity guard is
     * evaluated against fresh data (no over-receipt). Must be called inside a transaction.
     */
    Optional<PurchaseOrder> findByIdForUpdate(UUID id);

    Page<PurchaseOrder> findAll(Pageable pageable);

    Page<PurchaseOrder> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrder> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<PurchaseOrder> search(String q, Pageable pageable);

    Optional<PurchaseOrder> findFirstByDocumentNumberStartingWith(String prefix);
}
