package co.posinvent.domain.model;

import java.util.List;

public record ElectronicInvoiceResponse(
        String cufe,
        String qrCode,
        String status,
        String providerReference,
        String providerInvoiceId,
        String pdfUrl,
        String xmlUrl,
        List<ProviderError> errors
) {
    public ElectronicInvoiceResponse {
        if (errors == null) errors = List.of();
    }

    /** Backwards-compatible constructor for providers that only return the core fields. */
    public ElectronicInvoiceResponse(String cufe, String qrCode, String status, String providerReference) {
        this(cufe, qrCode, status, providerReference, null, null, null, List.of());
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
