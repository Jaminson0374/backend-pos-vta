package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single sales line.
 *
 * <p>{@code subtotal} stays the GROSS amount ({@code quantity × unitPrice}); the
 * taxable base of the line is {@code subtotal − discountAmount}. The line discount
 * (automatic promotion plus manual discount) is applied BEFORE IVA, reducing the
 * taxable base as required by the DIAN.</p>
 */
public record SaleItem(
        UUID id,
        UUID documentId,
        UUID productId,
        BigDecimal quantity,
        BigDecimal unitPrice,
        String taxType,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal subtotal,
        int lineNumber,
        UUID batchId,
        String discountType,
        BigDecimal discountValue,
        BigDecimal discountAmount,
        UUID promotionId
) {

    public static final String DISCOUNT_NONE = "NONE";
    public static final String DISCOUNT_PCT = "PCT";
    public static final String DISCOUNT_AMOUNT = "AMOUNT";

    public SaleItem {
        if (discountType == null || discountType.isBlank()) discountType = DISCOUNT_NONE;
        if (discountValue == null) discountValue = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
    }

    /** Backwards-compatible constructor for lines without discounts. */
    public SaleItem(
            UUID id,
            UUID documentId,
            UUID productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String taxType,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal subtotal,
            int lineNumber,
            UUID batchId
    ) {
        this(id, documentId, productId, quantity, unitPrice, taxType, taxRate, taxAmount, subtotal,
                lineNumber, batchId, DISCOUNT_NONE, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /** Taxable base of the line: gross subtotal minus the line-level discount (never negative). */
    public BigDecimal taxableBase() {
        return subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
    }
}
