package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single sales line.
 *
 * <p>{@code subtotal} stays the GROSS amount ({@code quantity × unitPrice}); the
 * taxable base of the line is {@code netAmount}, which includes the line discount and, for POS
 * sales, the prorated share of the document-level discount. The line discount is applied
 * BEFORE IVA, reducing the taxable base as required by the DIAN.</p>
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
        UUID promotionId,
        BigDecimal netAmount
) {

    public static final String DISCOUNT_NONE = "NONE";
    public static final String DISCOUNT_PCT = "PCT";
    public static final String DISCOUNT_AMOUNT = "AMOUNT";

    public SaleItem {
        if (discountType == null || discountType.isBlank()) discountType = DISCOUNT_NONE;
        if (discountValue == null) discountValue = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (netAmount == null) {
            netAmount = subtotal.subtract(discountAmount);
        }
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
                lineNumber, batchId, DISCOUNT_NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }

    /** Backwards-compatible constructor for lines with discounts but without an explicit net base. */
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
            UUID batchId,
            String discountType,
            BigDecimal discountValue,
            BigDecimal discountAmount,
            UUID promotionId
    ) {
        this(id, documentId, productId, quantity, unitPrice, taxType, taxRate, taxAmount, subtotal,
                lineNumber, batchId, discountType, discountValue, discountAmount, promotionId, null);
    }

    /** Taxable base of the line: net after line and (when applicable) document discount. */
    public BigDecimal taxableBase() {
        return netAmount;
    }

    /** Explicit DIAN-facing tax percentage for this line. */
    public BigDecimal taxPercentage() {
        return taxRate;
    }
}
