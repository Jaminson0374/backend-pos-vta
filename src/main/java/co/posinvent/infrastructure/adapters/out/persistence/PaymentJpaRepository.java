package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Page<PaymentEntity> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<PaymentEntity> findByIsAdvanceTrue(Pageable pageable);

    Page<PaymentEntity> findByIsAdvanceTrueAndSupplierId(UUID supplierId, Pageable pageable);

    @Query("""
            select coalesce(sum(p.remainingAdvance), 0)
            from PaymentEntity p
            where p.supplierId = :supplierId
              and p.isAdvance = true
              and p.remainingAdvance is not null
            """)
    BigDecimal sumRemainingAdvanceBySupplierId(@Param("supplierId") UUID supplierId);
}
