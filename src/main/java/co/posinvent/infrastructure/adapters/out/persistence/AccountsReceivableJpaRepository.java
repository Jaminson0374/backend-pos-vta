package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountsReceivableJpaRepository extends JpaRepository<AccountsReceivableEntity, UUID> {

    Page<AccountsReceivableEntity> findByClientIdOrderByDueDateDesc(UUID clientId, Pageable pageable);

    Page<AccountsReceivableEntity> findByStatusOrderByDueDateDesc(String status, Pageable pageable);

    Page<AccountsReceivableEntity> findByClientIdAndStatusOrderByDueDateDesc(UUID clientId, String status,
                                                                              Pageable pageable);

    List<AccountsReceivableEntity> findByDueDateBeforeAndStatusNot(LocalDate date, String status);

    Optional<AccountsReceivableEntity> findByDocumentId(UUID documentId);

    List<AccountsReceivableEntity> findByStatusAndOutstandingGreaterThanAndDueDateBefore(
            String status, java.math.BigDecimal outstanding, LocalDate dueDate);

    @Query("select coalesce(sum(a.outstanding), 0) from AccountsReceivableEntity a where a.clientId = :clientId")
    BigDecimal sumOutstandingByClientId(@Param("clientId") UUID clientId);
}
