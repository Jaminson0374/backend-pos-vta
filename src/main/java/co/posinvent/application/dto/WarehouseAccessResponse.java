package co.posinvent.application.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Warehouse access of the authenticated actor. {@code unrestricted} is true for ADMIN; otherwise
 * only the listed warehouse ids are accessible.
 */
public record WarehouseAccessResponse(boolean unrestricted, Set<UUID> warehouseIds) {
}
