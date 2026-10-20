package co.posinvent.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignWarehouseRequest(@NotNull UUID warehouseId) {
}
