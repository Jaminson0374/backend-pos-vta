package co.posinvent.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DianResolution(
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
    public DianResolution {
        if (active == null) active = false;
    }
}
