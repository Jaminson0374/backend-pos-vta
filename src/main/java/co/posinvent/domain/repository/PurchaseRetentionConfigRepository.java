package co.posinvent.domain.repository;

import co.posinvent.domain.model.PurchaseRetentionConfig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseRetentionConfigRepository {
    List<PurchaseRetentionConfig> findAllActive();
    List<PurchaseRetentionConfig> findAll();
    Optional<PurchaseRetentionConfig> findById(UUID id);
    Optional<PurchaseRetentionConfig> findByCode(String code);
    PurchaseRetentionConfig save(PurchaseRetentionConfig config);
    boolean existsByCode(String code);
    void deleteById(UUID id);
}
