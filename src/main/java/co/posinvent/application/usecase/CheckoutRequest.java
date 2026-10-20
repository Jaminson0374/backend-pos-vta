package co.posinvent.application.usecase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotNull UUID orderId,
        @NotNull UUID cashRegisterId,
        @NotNull UUID shiftId,
        @Size(min = 1) List<PaymentLine> payments,
        String documentDiscountType,
        BigDecimal documentDiscountValue,
        String discountReason,
        UUID discountAuthorizationId
) {

    public CheckoutRequest {
        if (documentDiscountType == null || documentDiscountType.isBlank()) documentDiscountType = "NONE";
        if (documentDiscountValue == null) documentDiscountValue = BigDecimal.ZERO;
    }

    /** Convenience constructor for a checkout without a document-level discount. */
    public CheckoutRequest(
            UUID orderId,
            UUID cashRegisterId,
            UUID shiftId,
            List<PaymentLine> payments
    ) {
        this(orderId, cashRegisterId, shiftId, payments, "NONE", BigDecimal.ZERO, null, null);
    }

    public record PaymentLine(
            @NotBlank String method,
            @NotNull BigDecimal amount
    ) {}
}
