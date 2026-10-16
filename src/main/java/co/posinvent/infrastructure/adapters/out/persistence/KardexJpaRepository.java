package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KardexJpaRepository extends JpaRepository<InventoryMovementEntity, UUID> {

    List<InventoryMovementEntity> findByReferenceTypeAndReferenceId(String referenceType, UUID referenceId);

    @Query("""
        SELECT m FROM InventoryMovementEntity m
        WHERE (:productId IS NULL OR m.productId = :productId)
          AND (:batchId IS NULL OR m.batchId = :batchId)
          AND (:warehouseId IS NULL OR m.warehouseId = :warehouseId)
          AND (:movementType IS NULL OR m.movementType = :movementType)
          AND m.createdAt >= :from
          AND m.createdAt <= :to
        ORDER BY m.createdAt DESC
        """)
    Page<InventoryMovementEntity> search(
            @Param("productId") UUID productId,
            @Param("batchId") UUID batchId,
            @Param("warehouseId") UUID warehouseId,
            @Param("movementType") String movementType,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(SUM(k.quantity), 0) FROM InventoryMovementEntity k WHERE k.productId = :productId")
    BigDecimal getCurrentStock(@Param("productId") UUID productId, @Param("warehouseId") UUID warehouseId);

    @Query(value = """
        SELECT AVG(unit_cost) FROM (
            SELECT DISTINCT ON (id) unit_cost
            FROM inventory_movements
            WHERE product_id = :productId
              AND movement_type IN ('ENTRY', 'PRODUCTION_OUTPUT')
            ORDER BY id
        ) sub
        """, nativeQuery = true)
    Optional<BigDecimal> getWeightedAverageCost(@Param("productId") UUID productId);

    @Query(value = """
        SELECT unit_cost FROM inventory_movements
        WHERE product_id = :productId
          AND movement_type IN ('ENTRY', 'PRODUCTION_OUTPUT')
        ORDER BY created_at ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<BigDecimal> getFifoCost(@Param("productId") UUID productId);
}
