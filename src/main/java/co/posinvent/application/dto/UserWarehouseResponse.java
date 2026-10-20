package co.posinvent.application.dto;

import java.util.UUID;

public record UserWarehouseResponse(UUID userId, UUID warehouseId) {
}
