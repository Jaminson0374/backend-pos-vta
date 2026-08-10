package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PurchaseRetentionConfigJpaRepository extends JpaRepository<PurchaseRetentionConfigEntity, UUID> {
    List<PurchaseRetentionConfigEntity> findByActiveTrueOrderBySortOrderAsc();
    List<PurchaseRetentionConfigEntity> findAllByOrderBySortOrderAsc();
    Optional<PurchaseRetentionConfigEntity> findByCode(String code);
    boolean existsByCode(String code);
}
