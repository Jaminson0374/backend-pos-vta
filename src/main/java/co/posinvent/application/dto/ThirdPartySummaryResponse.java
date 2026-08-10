package co.posinvent.application.dto;

import co.posinvent.domain.model.ThirdParty;

import java.util.UUID;

/**
 * Lightweight DTO for dropdowns/autocompletes — id, name, and numIdentification only.
 */
public record ThirdPartySummaryResponse(
    UUID id,
    String name,
    String numIdentification,
    String email
) {
    public static ThirdPartySummaryResponse from(ThirdParty t) {
        return new ThirdPartySummaryResponse(
            t.id(),
            t.name(),
            t.numIdentification(),
            t.email()
        );
    }
}
