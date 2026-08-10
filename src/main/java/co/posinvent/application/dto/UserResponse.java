package co.posinvent.application.dto;

import co.posinvent.domain.model.User;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    RoleSummary role,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String tempPassword,
    UUID employeeId,
    String employeeName
) {
    public record RoleSummary(UUID id, String name) {}

    public static UserResponse from(User u) {
        return new UserResponse(
            u.id(),
            u.username(),
            u.fullName(),
            u.email(),
            u.role() != null ? new RoleSummary(u.role().id(), u.role().name()) : null,
            u.isActive(),
            u.createdAt(),
            u.updatedAt(),
            null,
            u.employeeId(),
            u.employeeName()
        );
    }

    public static UserResponse withTempPassword(User u, String tempPassword) {
        return new UserResponse(
            u.id(),
            u.username(),
            u.fullName(),
            u.email(),
            u.role() != null ? new RoleSummary(u.role().id(), u.role().name()) : null,
            u.isActive(),
            u.createdAt(),
            u.updatedAt(),
            tempPassword,
            u.employeeId(),
            u.employeeName()
        );
    }
}
