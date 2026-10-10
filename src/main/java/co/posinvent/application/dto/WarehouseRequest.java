package co.posinvent.application.dto;

import co.posinvent.domain.model.Warehouse.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WarehouseRequest(
        @NotBlank @Size(max = 150) String name,
        @NotNull WarehouseType warehouseType,
        @Size(max = 255) String location
) {}
