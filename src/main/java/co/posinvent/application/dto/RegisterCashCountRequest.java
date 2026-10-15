package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request body to register a historical cash count (arqueo) for a shift.
 * The counted total is derived from {@code lines}; the client never sends it.
 */
public record RegisterCashCountRequest(
        @NotEmpty @Valid List<CashCountLineRequest> lines,
        String notes
) {
}
