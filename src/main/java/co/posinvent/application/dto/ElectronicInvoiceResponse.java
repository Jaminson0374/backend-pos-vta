package co.posinvent.application.dto;

import co.posinvent.domain.model.ElectronicInvoice;
import co.posinvent.domain.model.ElectronicInvoiceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ElectronicInvoiceResponse(
    UUID id,
    UUID salesDocumentId,
    UUID sourceDocumentId,
    String cufe,
    String qrCode,
    ElectronicInvoiceStatus status,
    OffsetDateTime sentAt,
    OffsetDateTime responseAt,
    OffsetDateTime createdAt,
    String providerInvoiceId,
    String pdfUrl,
    String xmlUrl,
    java.util.List<co.posinvent.domain.model.ProviderError> errors
) {
    /** Backwards-compatible constructor for callers without provider artifacts. */
    public ElectronicInvoiceResponse(
            UUID id, UUID salesDocumentId, UUID sourceDocumentId, String cufe, String qrCode,
            ElectronicInvoiceStatus status, OffsetDateTime sentAt, OffsetDateTime responseAt,
            OffsetDateTime createdAt) {
        this(id, salesDocumentId, sourceDocumentId, cufe, qrCode, status, sentAt, responseAt,
                createdAt, null, null, null, java.util.List.of());
    }

    public static ElectronicInvoiceResponse from(ElectronicInvoice e) {
        return new ElectronicInvoiceResponse(
            e.id(), e.salesDocumentId(), e.sourceDocumentId(),
            e.cufe(), e.qrCode(), e.status(),
            e.sentAt(), e.responseAt(), e.createdAt(),
            e.providerInvoiceId(), e.pdfUrl(), e.xmlUrl(), e.providerErrors()
        );
    }
}
