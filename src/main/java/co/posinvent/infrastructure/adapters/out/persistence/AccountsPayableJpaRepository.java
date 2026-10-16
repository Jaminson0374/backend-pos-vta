package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsPayableJpaRepository extends JpaRepository<AccountsPayableEntity, UUID> {

    Page<AccountsPayableEntity> findBySupplierIdOrderByDueDateDesc(UUID supplierId, Pageable pageable);

    Page<AccountsPayableEntity> findByStatusOrderByDueDateDesc(String status, Pageable pageable);

    Page<AccountsPayableEntity> findBySupplierIdAndStatusOrderByDueDateDesc(UUID supplierId, String status,
                                                                              Pageable pageable);

    List<AccountsPayableEntity> findByDueDateBeforeAndStatusNot(LocalDate date, String status);

    Optional<AccountsPayableEntity> findByDocumentId(UUID documentId);

    /**
     * Locks the CxP row of an invoice for the duration of the transaction, so concurrent payments
     * cannot both read the same outstanding and lose an update.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountsPayableEntity a where a.documentId = :documentId")
    Optional<AccountsPayableEntity> findByDocumentIdForUpdate(@Param("documentId") UUID documentId);

    @Query("select coalesce(sum(a.outstanding), 0) from AccountsPayableEntity a where a.supplierId = :supplierId")
    BigDecimal sumOutstandingBySupplierId(@Param("supplierId") UUID supplierId);
}
