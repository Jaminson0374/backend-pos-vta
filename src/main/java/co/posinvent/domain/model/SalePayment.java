package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One tender line received for a sale at POS checkout, linked to the issued
 * invoice ({@code sales_document_id}). Kept separate from the supplier
 * {@link Payment} module, which models accounts payable (CxP).
 */
public record SalePayment(
        UUID id,
        UUID salesDocumentId,
        String method,
        BigDecimal amount,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
