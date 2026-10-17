package co.posinvent.application.dto;

import co.posinvent.domain.model.Payment;
import co.posinvent.domain.model.Payment.InvoicePayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        BigDecimal amount,
        LocalDate paymentDate,
        String method,
        String reference,
        String notes,
        UUID createdBy,
        OffsetDateTime createdAt,
        List<InvoicePaymentBreakdown> appliedBreakdown
) {
    public record InvoicePaymentBreakdown(
            UUID invoiceId,
            BigDecimal appliedAmount
    ) {}

    public static PaymentResponse from(Payment payment) {
        return from(payment, null);
    }

    public static PaymentResponse from(Payment payment, String supplierName) {
        return new PaymentResponse(
                payment.id(),
                payment.supplierId(),
                supplierName,
                payment.amount(),
                payment.paymentDate(),
                payment.method(),
                payment.reference(),
                payment.notes(),
                payment.createdBy(),
                payment.createdAt(),
                payment.invoicePayments().stream()
                        .map(ip -> new InvoicePaymentBreakdown(ip.invoiceId(), ip.appliedAmount()))
                        .toList()
        );
    }
}
