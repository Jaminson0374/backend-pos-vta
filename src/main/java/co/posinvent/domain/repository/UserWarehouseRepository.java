package co.posinvent.domain.repository;

import java.util.Set;
import java.util.UUID;

public interface UserWarehouseRepository {

    Set<UUID> findWarehouseIdsByUserId(UUID userId);

    boolean exists(UUID userId, UUID warehouseId);

    void assign(UUID userId, UUID warehouseId);

    void remove(UUID userId, UUID warehouseId);
}
