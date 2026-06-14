package co.posinvent.application.dto;

import co.posinvent.domain.model.AccountsPayable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountsPayableResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        UUID documentId,
        String documentNumber,
        UUID debitCreditNoteId,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstanding,
        LocalDate dueDate,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountsPayableResponse from(AccountsPayable ap) {
        return new AccountsPayableResponse(
                ap.id(),
                ap.supplierId(),
                null,
                ap.documentId(),
                null,
                ap.debitCreditNoteId(),
                ap.totalAmount(),
                ap.paidAmount(),
                ap.outstanding(),
                ap.dueDate(),
                ap.status().name(),
                ap.createdAt(),
                ap.updatedAt()
        );
    }

    public static AccountsPayableResponse from(AccountsPayable ap, String supplierName) {
        return new AccountsPayableResponse(
                ap.id(),
                ap.supplierId(),
                supplierName,
                ap.documentId(),
                null,
                ap.debitCreditNoteId(),
                ap.totalAmount(),
                ap.paidAmount(),
                ap.outstanding(),
                ap.dueDate(),
                ap.status().name(),
                ap.createdAt(),
                ap.updatedAt()
        );
    }
}
