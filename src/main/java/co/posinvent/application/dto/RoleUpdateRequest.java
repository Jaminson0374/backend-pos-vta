package co.posinvent.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleUpdateRequest(
    @NotBlank String permissions
) {}
