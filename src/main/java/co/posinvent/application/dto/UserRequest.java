package co.posinvent.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserRequest(
    @NotNull UUID employeeId,
    @Email @Size(max = 200) String email,
    @NotNull UUID roleId,
    @NotNull Boolean isActive
) {}
