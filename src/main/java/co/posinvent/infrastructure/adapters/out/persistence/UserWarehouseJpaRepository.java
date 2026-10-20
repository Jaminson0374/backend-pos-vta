package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface UserWarehouseJpaRepository
        extends JpaRepository<UserWarehouseEntity, UserWarehouseId> {

    @Query("select uw.id.warehouseId from UserWarehouseEntity uw where uw.id.userId = :userId")
    Set<UUID> findWarehouseIdsByUserId(@Param("userId") UUID userId);
}
