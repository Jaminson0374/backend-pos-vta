package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.WarehouseResponse;
import co.posinvent.application.port.in.WarehousePort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {

    private final WarehousePort warehouseUseCase;

    public WarehouseController(WarehousePort warehouseUseCase) {
        this.warehouseUseCase = warehouseUseCase;
    }

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> listActive() {
        return ResponseEntity.ok(warehouseUseCase.listActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(warehouseUseCase.getById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<WarehouseResponse>> search(@RequestParam String query) {
        return ResponseEntity.ok(warehouseUseCase.searchByName(query));
    }
}
