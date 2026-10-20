package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.repository.UserWarehouseRepository;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
class UserWarehouseRepositoryAdapter implements UserWarehouseRepository {

    private final UserWarehouseJpaRepository jpa;

    UserWarehouseRepositoryAdapter(UserWarehouseJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Set<UUID> findWarehouseIdsByUserId(UUID userId) {
        return new HashSet<>(jpa.findWarehouseIdsByUserId(userId));
    }

    @Override
    public boolean exists(UUID userId, UUID warehouseId) {
        return jpa.existsById(new UserWarehouseId(userId, warehouseId));
    }

    @Override
    public void assign(UUID userId, UUID warehouseId) {
        jpa.assignIfAbsent(userId, warehouseId);
    }

    @Override
    public void remove(UUID userId, UUID warehouseId) {
        jpa.deleteIfPresent(userId, warehouseId);
    }
}
