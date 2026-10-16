package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrderEntity, UUID> {

    Page<PurchaseOrderEntity> findByStatus(PurchaseOrderStatus status, Pageable pageable);

    Page<PurchaseOrderEntity> findBySupplierId(UUID supplierId, Pageable pageable);

    /**
     * Locks the OC row for the duration of the transaction, so concurrent goods receipts of the
     * same OC cannot both read the same pending quantity and over-receive.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PurchaseOrderEntity p WHERE p.id = :id")
    Optional<PurchaseOrderEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT p FROM PurchaseOrderEntity p
        WHERE (:q IS NULL OR :q = '' OR LOWER(p.documentNumber) LIKE :q
               OR p.supplierId IN (SELECT t.id FROM ThirdPartyEntity t WHERE LOWER(t.name) LIKE :q))
        ORDER BY p.orderDate DESC
        """)
    Page<PurchaseOrderEntity> search(@Param("q") String q, Pageable pageable);

    Optional<PurchaseOrderEntity> findFirstByDocumentNumberStartingWithOrderByDocumentNumberDesc(String prefix);
}
