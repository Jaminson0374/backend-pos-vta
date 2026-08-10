package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record User(
    UUID id,
    String username,
    String fullName,
    String email,
    Role role,
    boolean isActive,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    UUID employeeId,
    String employeeName
) {}
