package co.posinvent.application.dto;

import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.SaleItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SalesDocumentResponse(
        UUID id,
        SalesDocumentType type,
        SalesDocumentStatus status,
        String documentNumber,
        UUID clientId,
        String clientName,
        UUID warehouseId,
        UUID shiftId,
        UUID cashRegisterId,
        UUID sourceDocumentId,
        BigDecimal totalNet,
        BigDecimal totalTax0,
        BigDecimal totalTax5,
        BigDecimal totalTax19,
        BigDecimal totalAmount,
        UUID createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<SaleItemResponse> items,
        LocalDate dueDate,
        Boolean isCreditSale,
        String dianCufe,
        String dianStatus,
        String dianProviderInvoiceId,
        String dianPdfUrl,
        String dianXmlUrl,
        String dianQrUrl,
        OffsetDateTime providerSyncedAt
) {
    public static SalesDocumentResponse from(SalesDocument doc) {
        return new SalesDocumentResponse(
                doc.id(),
                doc.type(),
                doc.status(),
                doc.documentNumber(),
                doc.clientId(),
                null,
                doc.warehouseId(),
                doc.shiftId(),
                doc.cashRegisterId(),
                doc.sourceDocumentId(),
                doc.totalNet(),
                doc.totalTax0(),
                doc.totalTax5(),
                doc.totalTax19(),
                doc.totalAmount(),
                doc.createdBy(),
                doc.createdAt(),
                doc.updatedAt(),
                doc.items().stream()
                        .map(SaleItemResponse::from)
                        .toList(),
                doc.dueDate(),
                doc.isCreditSale(),
                doc.dianCufe(),
                doc.dianStatus(),
                doc.dianProviderInvoiceId(),
                doc.dianPdfUrl(),
                doc.dianXmlUrl(),
                doc.dianQrUrl(),
                doc.providerSyncedAt()
        );
    }
}
