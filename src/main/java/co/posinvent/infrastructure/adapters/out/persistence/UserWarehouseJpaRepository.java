package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface UserWarehouseJpaRepository
        extends JpaRepository<UserWarehouseEntity, UserWarehouseId> {

    @Query("select uw.id.warehouseId from UserWarehouseEntity uw where uw.id.userId = :userId")
    Set<UUID> findWarehouseIdsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO user_warehouses (user_id, warehouse_id)
            VALUES (:userId, :warehouseId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    void assignIfAbsent(@Param("userId") UUID userId, @Param("warehouseId") UUID warehouseId);

    @Modifying
    @Query(value = """
            DELETE FROM user_warehouses
            WHERE user_id = :userId AND warehouse_id = :warehouseId
            """, nativeQuery = true)
    void deleteIfPresent(@Param("userId") UUID userId, @Param("warehouseId") UUID warehouseId);
}
