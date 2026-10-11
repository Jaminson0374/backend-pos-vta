package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SalePaymentJpaRepository extends JpaRepository<SalePaymentEntity, UUID> {

    List<SalePaymentEntity> findBySalesDocumentId(UUID salesDocumentId);
}
