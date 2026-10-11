package co.posinvent.application.dto;

import co.posinvent.domain.model.DianResolution;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DianResolutionResponse(
    UUID id,
    String resolutionNumber,
    LocalDate resolutionDate,
    LocalDate validFrom,
    LocalDate validTo,
    String prefix,
    Long rangeFrom,
    Long rangeTo,
    Long currentNumber,
    String softwarePin,
    Boolean active,
    OffsetDateTime createdAt
) {
    public static DianResolutionResponse from(DianResolution r) {
        return new DianResolutionResponse(
            r.id(), r.resolutionNumber(), r.resolutionDate(),
            r.validFrom(), r.validTo(), r.prefix(),
            r.rangeFrom(), r.rangeTo(), r.currentNumber(), r.softwarePin(),
            r.active(), r.createdAt()
        );
    }
}
