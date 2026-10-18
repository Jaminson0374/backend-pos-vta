package co.posinvent.application.dto;

import co.posinvent.domain.model.DigitalCertificate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Admin-facing metadata for a digital certificate.
 *
 * <p>Deliberately excludes the PKCS12 bytes and the keystore password (encrypted
 * or not): the API must never expose secrets, even in encrypted form.</p>
 */
public record DigitalCertificateResponse(
    UUID id,
    String alias,
    String issuer,
    String subject,
    LocalDate expirationDate,
    String status,
    OffsetDateTime uploadedAt
) {
    public static DigitalCertificateResponse from(DigitalCertificate certificate) {
        return new DigitalCertificateResponse(
            certificate.id(),
            certificate.name(),
            certificate.issuer(),
            certificate.subject(),
            certificate.validUntil(),
            Boolean.TRUE.equals(certificate.active()) ? "ACTIVE" : "INACTIVE",
            certificate.createdAt()
        );
    }
}
