package co.posinvent.application.port.in;

import co.posinvent.application.dto.WarehouseRequest;
import co.posinvent.application.dto.WarehouseResponse;

import java.util.List;
import java.util.UUID;

public interface WarehousePort {

    List<WarehouseResponse> listActive();

    WarehouseResponse getById(UUID id);

    List<WarehouseResponse> searchByName(String query);

    WarehouseResponse create(WarehouseRequest request);

    WarehouseResponse update(UUID id, WarehouseRequest request);
}
