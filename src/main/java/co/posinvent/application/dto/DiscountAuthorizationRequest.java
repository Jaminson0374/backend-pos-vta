package co.posinvent.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * Administrator credentials used to approve a discount that exceeds policy.
 * The credentials are validated here and never forwarded to the checkout.
 */
public record DiscountAuthorizationRequest(
        @NotBlank String username,
        @NotBlank String password,
        UUID cashRegisterId
) {
}
