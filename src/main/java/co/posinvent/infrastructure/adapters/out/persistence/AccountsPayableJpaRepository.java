package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
