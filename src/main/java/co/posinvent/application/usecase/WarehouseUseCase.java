package co.posinvent.application.usecase;

import co.posinvent.application.dto.WarehouseRequest;
import co.posinvent.application.dto.WarehouseResponse;
import co.posinvent.application.port.in.WarehousePort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class WarehouseUseCase implements WarehousePort {

    private final WarehouseRepository warehouseRepository;

    public WarehouseUseCase(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> listActive() {
        return warehouseRepository.findAllActive().stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(UUID id) {
        return warehouseRepository.findById(id)
                .map(WarehouseResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", id));
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> searchByName(String query) {
        return warehouseRepository.findByNameContaining(query).stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        var warehouse = new Warehouse(
                null,
                request.name(),
                request.location(),
                request.warehouseType(),
                true,
                null
        );
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        var existing = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", id));

        var updated = new Warehouse(
                existing.id(),
                request.name(),
                request.location(),
                request.warehouseType(),
                existing.active(),
                existing.createdAt()
        );
        return WarehouseResponse.from(warehouseRepository.save(updated));
    }
}
