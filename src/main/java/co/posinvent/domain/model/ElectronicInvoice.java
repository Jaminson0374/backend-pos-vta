package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ElectronicInvoice(
    UUID id,
    UUID salesDocumentId,
    UUID sourceDocumentId,
    String cufe,
    String qrCode,
    Map<String, Object> providerResponse,
    ElectronicInvoiceStatus status,
    OffsetDateTime sentAt,
    OffsetDateTime responseAt,
    OffsetDateTime createdAt,
    String providerInvoiceId,
    String pdfUrl,
    String xmlUrl,
    List<ProviderError> providerErrors
) {
    public ElectronicInvoice {
        if (providerErrors == null) providerErrors = List.of();
    }

    public ElectronicInvoice(
            UUID id,
            UUID salesDocumentId,
            UUID sourceDocumentId,
            String cufe,
            String qrCode,
            Map<String, Object> providerResponse,
            ElectronicInvoiceStatus status,
            OffsetDateTime sentAt,
            OffsetDateTime responseAt,
            OffsetDateTime createdAt
    ) {
        this(id, salesDocumentId, sourceDocumentId, cufe, qrCode, providerResponse, status,
                sentAt, responseAt, createdAt, null, null, null, List.of());
    }
}
