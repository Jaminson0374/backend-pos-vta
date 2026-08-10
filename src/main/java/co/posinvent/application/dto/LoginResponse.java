package co.posinvent.application.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        String role,
        UUID userId,
        String fullName,
        UUID employeeId
) {}
