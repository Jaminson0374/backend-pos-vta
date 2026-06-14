package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseReportResponse(
        List<MonthlySummary> monthlySummaries,
        BigDecimal totalPurchased,
        int totalOrders,
        int totalReceipts
) {
    public record MonthlySummary(
            String month,
            BigDecimal total,
            int orderCount,
            int receiptCount
    ) {}
}
