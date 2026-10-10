package co.posinvent.domain.repository;

import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.model.Warehouse.WarehouseType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository {

    List<Warehouse> findAllActive();

    Optional<Warehouse> findById(UUID id);

    Optional<Warehouse> findFirstActiveByType(WarehouseType type);

    List<Warehouse> findByNameContaining(String query);

    Warehouse save(Warehouse warehouse);
}
