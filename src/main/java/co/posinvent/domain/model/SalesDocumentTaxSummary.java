package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic 0/5/19 tax breakdown derived from the document lines.
 *
 * <p>This is the single source for the provider payload. It intentionally recomputes from
 * {@link SaleItem#netAmount()} and {@link SaleItem#taxAmount()} instead of trusting the
 * aggregate columns, then validates those columns against the result. A mismatch must fail
 * closed: Siigo would reject the document if its own totals differ from ours.</p>
 */
public record SalesDocumentTaxSummary(
        List<TaxRateSubtotal> subtotals,
        BigDecimal netAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal RATE_5 = new BigDecimal("5.00");
    private static final BigDecimal RATE_19 = new BigDecimal("19.00");

    public record TaxRateSubtotal(
            BigDecimal rate,
            BigDecimal taxableBase,
            BigDecimal taxAmount
    ) {}

    public static SalesDocumentTaxSummary from(SalesDocument document) {
        Map<BigDecimal, TaxRateSubtotal> grouped = new LinkedHashMap<>();
        grouped.put(ZERO, new TaxRateSubtotal(ZERO, ZERO, ZERO));
        grouped.put(RATE_5, new TaxRateSubtotal(RATE_5, ZERO, ZERO));
        grouped.put(RATE_19, new TaxRateSubtotal(RATE_19, ZERO, ZERO));

        for (var item : document.items()) {
            var rate = canonicalRate(item.taxRate());
            var current = grouped.get(rate);
            grouped.put(rate, new TaxRateSubtotal(
                    rate,
                    current.taxableBase().add(nullToZero(item.netAmount())),
                    current.taxAmount().add(nullToZero(item.taxAmount()))
            ));
        }

        var subtotals = List.copyOf(grouped.values());
        var net = subtotals.stream()
                .map(TaxRateSubtotal::taxableBase)
                .reduce(ZERO, BigDecimal::add);
        var tax = subtotals.stream()
                .map(TaxRateSubtotal::taxAmount)
                .reduce(ZERO, BigDecimal::add);

        return new SalesDocumentTaxSummary(subtotals, net, tax, net.add(tax));
    }

    /** Recomputes and verifies the persisted aggregate columns; throws on any drift. */
    public static SalesDocumentTaxSummary verifiedAgainst(SalesDocument document) {
        var summary = from(document);
        summary.validateAgainst(document);
        return summary;
    }

    public void validateAgainst(SalesDocument document) {
        var rate0 = subtotal(ZERO).taxAmount();
        var rate5 = subtotal(RATE_5).taxAmount();
        var rate19 = subtotal(RATE_19).taxAmount();

        requireEqual("totalNet", netAmount, document.totalNet());
        requireEqual("totalTax0", rate0, document.totalTax0());
        requireEqual("totalTax5", rate5, document.totalTax5());
        requireEqual("totalTax19", rate19, document.totalTax19());
        requireEqual("totalAmount", totalAmount, document.totalAmount());
    }

    public TaxRateSubtotal subtotal(BigDecimal rate) {
        var canonical = canonicalRate(rate);
        return subtotals.stream()
                .filter(s -> s.rate().compareTo(canonical) == 0)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "DIAN_TAX_BREAKDOWN_MISMATCH",
                        "No hay subtotal para la tarifa " + canonical));
    }

    private static BigDecimal canonicalRate(BigDecimal rate) {
        if (rate == null) return ZERO;
        if (rate.compareTo(BigDecimal.ZERO) == 0) return ZERO;
        if (rate.compareTo(RATE_5) == 0) return RATE_5;
        if (rate.compareTo(RATE_19) == 0) return RATE_19;
        throw new BusinessException("DIAN_UNSUPPORTED_TAX_RATE",
                "Tarifa no soportada para DIAN: " + rate + ". Solo se admiten 0, 5 y 19.");
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : ZERO;
    }

    private static void requireEqual(String field, BigDecimal calculated, BigDecimal persisted) {
        var expected = persisted != null ? persisted : ZERO;
        if (calculated.compareTo(expected) != 0) {
            throw new BusinessException("DIAN_TAX_BREAKDOWN_MISMATCH",
                    "El desglose de impuestos no cuadra en " + field
                            + ": calculado=" + calculated + ", persistido=" + expected);
        }
    }
}
