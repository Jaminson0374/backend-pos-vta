package co.posinvent.application.dto;

import co.posinvent.domain.model.Role;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleResponse(
    UUID id,
    String name,
    OffsetDateTime createdAt
) {
    public static RoleResponse from(Role r) {
        return new RoleResponse(r.id(), r.name(), r.createdAt());
    }
}
