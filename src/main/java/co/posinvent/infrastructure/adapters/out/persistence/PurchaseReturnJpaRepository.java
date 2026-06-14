package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PurchaseReturnJpaRepository extends JpaRepository<PurchaseReturnEntity, UUID> {

    Optional<PurchaseReturnEntity> findByReceiptId(UUID receiptId);

    Optional<PurchaseReturnEntity> findFirstByDocumentNumberStartingWithOrderByDocumentNumberDesc(String prefix);
}
