package co.posinvent.application.dto;

import java.math.BigDecimal;

public record PurchaseSalesComparisonResponse(
        int year,
        int month,
        BigDecimal totalPurchases,
        BigDecimal totalSales,
        BigDecimal margin,
        BigDecimal marginPct
) {}
