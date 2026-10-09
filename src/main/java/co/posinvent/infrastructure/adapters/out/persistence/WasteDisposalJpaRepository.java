package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WasteDisposalJpaRepository extends JpaRepository<WasteDisposalEntity, UUID> {

    @Query(value = """
        SELECT
            b.id AS batch_id,
            p.name AS product_name,
            w.name AS warehouse_name,
            b.expiration_date,
            COALESCE(s.current_quantity, 0) AS current_qty,
            (SELECT is3.product_id FROM inventory_stock is3 WHERE is3.batch_id = b.id LIMIT 1) AS product_id,
            b.warehouse_id
        FROM batches b
        JOIN products p ON p.id = (
            SELECT DISTINCT is2.product_id FROM inventory_stock is2 WHERE is2.batch_id = b.id LIMIT 1
        )
        JOIN warehouses w ON w.id = b.warehouse_id
        LEFT JOIN inventory_stock s ON s.batch_id = b.id
        WHERE b.expiration_date IS NOT NULL
          AND b.expiration_date BETWEEN NOW() AND NOW() + (:days * INTERVAL '1 day')
          AND b.status <> 'CLOSED'
        ORDER BY b.expiration_date ASC
        """, nativeQuery = true)
    List<Map<String, Object>> findExpiringBatchesNative(@Param("days") int days);

    @Query(value = """
        SELECT
            b.id AS batch_id,
            p.name AS product_name,
            w.name AS warehouse_name,
            b.expiration_date,
            COALESCE(s.current_quantity, 0) AS current_qty,
            (SELECT is3.product_id FROM inventory_stock is3 WHERE is3.batch_id = b.id LIMIT 1) AS product_id,
            b.warehouse_id
        FROM batches b
        JOIN products p ON p.id = (
            SELECT DISTINCT is2.product_id FROM inventory_stock is2 WHERE is2.batch_id = b.id LIMIT 1
        )
        JOIN warehouses w ON w.id = b.warehouse_id
        LEFT JOIN inventory_stock s ON s.batch_id = b.id
        WHERE b.expiration_date IS NOT NULL
          AND b.expiration_date < NOW()
          AND b.status <> 'CLOSED'
        ORDER BY b.expiration_date ASC
        """, nativeQuery = true)
    List<Map<String, Object>> findExpiredBatchesNative();
}
