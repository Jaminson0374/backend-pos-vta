package co.posinvent.domain.model;

import co.posinvent.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesDocumentTaxSummaryTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Test
    void groupsNetBaseAndTaxStrictlyByRate() {
        var item0 = item("0", "0.00", "100.00");
        var item5 = item("5", "5.00", "100.00");
        var item19 = item("19", "19.00", "100.00");
        var document = document(
                List.of(item0, item5, item19),
                "300.00", "0.00", "5.00", "19.00", "324.00");

        var summary = document.taxSummary();

        assertThat(summary.subtotals()).extracting(SalesDocumentTaxSummary.TaxRateSubtotal::rate)
                .containsExactly(new BigDecimal("0.00"), new BigDecimal("5.00"), new BigDecimal("19.00"));
        assertThat(summary.subtotal(new BigDecimal("0.00")).taxableBase()).isEqualByComparingTo("100.00");
        assertThat(summary.subtotal(new BigDecimal("5.00")).taxableBase()).isEqualByComparingTo("100.00");
        assertThat(summary.subtotal(new BigDecimal("19.00")).taxableBase()).isEqualByComparingTo("100.00");
        assertThat(summary.netAmount()).isEqualByComparingTo("300.00");
        assertThat(summary.taxAmount()).isEqualByComparingTo("24.00");
        assertThat(summary.totalAmount()).isEqualByComparingTo("324.00");
    }

    @Test
    void usesExplicitNetAmountInsteadOfTheGrossSubtotal() {
        var discounted = new SaleItem(
                null, null, UUID.randomUUID(), BigDecimal.ONE, new BigDecimal("100.00"),
                "IVA_19", new BigDecimal("19.00"), new BigDecimal("18.05"),
                new BigDecimal("100.00"), 1, null,
                SaleItem.DISCOUNT_NONE, BigDecimal.ZERO, BigDecimal.ZERO, null,
                new BigDecimal("95.00"));
        var document = document(
                List.of(discounted),
                "95.00", "0.00", "0.00", "18.05", "113.05");

        var summary = document.taxSummary();

        assertThat(summary.subtotal(new BigDecimal("19.00")).taxableBase())
                .isEqualByComparingTo("95.00");
        assertThat(summary.taxAmount()).isEqualByComparingTo("18.05");
    }

    @Test
    void rejectsPersistedTotalsThatDoNotMatchTheLineBreakdown() {
        var document = document(
                List.of(item("19", "19.00", "100.00")),
                "100.00", "0.00", "0.00", "18.00", "119.00");

        assertThatThrownBy(document::taxSummary)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_TAX_BREAKDOWN_MISMATCH"));
    }

    @Test
    void rejectsRatesOutsideTheSupportedSet() {
        var document = document(
                List.of(item("8", "8.00", "100.00")),
                "100.00", "0.00", "0.00", "0.00", "108.00");

        assertThatThrownBy(document::taxSummary)
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DIAN_UNSUPPORTED_TAX_RATE"));
    }

    private SaleItem item(String taxType, String rate, String net) {
        var taxRate = new BigDecimal(rate);
        var netAmount = new BigDecimal(net);
        var tax = netAmount.multiply(taxRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        return new SaleItem(
                null, null, UUID.randomUUID(), BigDecimal.ONE, netAmount,
                taxType.equals("0") ? "EXENTO" : "IVA_" + taxType,
                taxRate, tax, netAmount, 1, null,
                SaleItem.DISCOUNT_NONE, BigDecimal.ZERO, BigDecimal.ZERO, null, netAmount);
    }

    private SalesDocument document(
            List<SaleItem> items,
            String net, String tax0, String tax5, String tax19, String total) {
        return new SalesDocument(
                null, SalesDocumentType.INVOICE, SalesDocumentStatus.ISSUED, "FV-1",
                CLIENT_ID, WAREHOUSE_ID, null, null, null,
                new BigDecimal(net), new BigDecimal(tax0), new BigDecimal(tax5),
                new BigDecimal(tax19), new BigDecimal(total), UUID.randomUUID(),
                OffsetDateTime.now(), null, items, null, false, null);
    }
}
