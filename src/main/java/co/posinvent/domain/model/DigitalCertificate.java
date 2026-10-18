package co.posinvent.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DigitalCertificate(
    UUID id,
    String name,
    String issuer,
    String subject,
    byte[] certificateData,
    String passwordEncrypted,
    LocalDate validUntil,
    Boolean active,
    OffsetDateTime createdAt
) {}
