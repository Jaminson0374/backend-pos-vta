package co.posinvent.domain.model;

import java.math.BigDecimal;

public record SalesByPeriodRow(
        String period,
        int totalInvoices,
        BigDecimal totalRevenue,
        BigDecimal totalNet,
        BigDecimal totalTax,
        BigDecimal totalDiscount
) {

    public SalesByPeriodRow {
        if (totalDiscount == null) totalDiscount = BigDecimal.ZERO;
    }
}
