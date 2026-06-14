package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ProductPurchaseResponse;
import co.posinvent.application.dto.PurchaseReportResponse;
import co.posinvent.application.dto.PurchaseSalesComparisonResponse;
import co.posinvent.application.dto.SupplierPurchaseResponse;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@RestController
@RequestMapping("/api/v1/purchase-reports")
@PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
public class PurchaseReportController {

    private final JdbcTemplate jdbc;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ProductRepository productRepository;

    public PurchaseReportController(
            JdbcTemplate jdbc,
            ThirdPartyRepository thirdPartyRepository,
            ProductRepository productRepository
    ) {
        this.jdbc = jdbc;
        this.thirdPartyRepository = thirdPartyRepository;
        this.productRepository = productRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<PurchaseReportResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        // Monthly summary: Σ(line.orderedQty × line.unitCost) per month
        var monthlyRows = jdbc.queryForList("""
            SELECT
                TO_CHAR(po.order_date, 'YYYY-MM') AS month,
                COALESCE(SUM(li.ordered_qty * li.unit_cost), 0) AS total,
                COUNT(DISTINCT po.id) AS order_count
            FROM purchase_orders po
            JOIN purchase_line_items li ON li.oc_id = po.id
            WHERE po.order_date >= ? AND po.order_date <= ?
            GROUP BY TO_CHAR(po.order_date, 'YYYY-MM')
            ORDER BY month
            """, startDate, endDate);

        // Count receipts by month
        var receiptRows = jdbc.queryForList("""
            SELECT
                TO_CHAR(gr.receipt_date, 'YYYY-MM') AS month,
                COUNT(DISTINCT gr.id) AS receipt_count
            FROM goods_receipts gr
            WHERE gr.receipt_date >= ? AND gr.receipt_date <= ?
            GROUP BY TO_CHAR(gr.receipt_date, 'YYYY-MM')
            """, startDate, endDate);

        Map<String, Integer> receiptByMonth = new HashMap<>();
        for (var row : receiptRows) {
            receiptByMonth.put(
                (String) row.get("month"),
                ((Number) row.get("receipt_count")).intValue()
            );
        }

        List<PurchaseReportResponse.MonthlySummary> summaries = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int grandOrders = 0;
        int grandReceipts = 0;

        for (var row : monthlyRows) {
            String month = (String) row.get("month");
            BigDecimal total = toBigDecimal(row.get("total"));
            int orderCount = ((Number) row.get("order_count")).intValue();
            int receiptCount = receiptByMonth.getOrDefault(month, 0);

            summaries.add(new PurchaseReportResponse.MonthlySummary(month, total, orderCount, receiptCount));
            grandTotal = grandTotal.add(total);
            grandOrders += orderCount;
            grandReceipts += receiptCount;
        }

        return ResponseEntity.ok(new PurchaseReportResponse(summaries, grandTotal, grandOrders, grandReceipts));
    }

    @GetMapping("/by-supplier")
    public ResponseEntity<List<SupplierPurchaseResponse>> bySupplier(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        var rows = jdbc.queryForList("""
            SELECT
                po.supplier_id,
                COALESCE(SUM(li.ordered_qty * li.unit_cost), 0) AS total_purchased,
                COUNT(DISTINCT po.id) AS order_count
            FROM purchase_orders po
            JOIN purchase_line_items li ON li.oc_id = po.id
            WHERE po.order_date >= ? AND po.order_date <= ?
            GROUP BY po.supplier_id
            ORDER BY total_purchased DESC
            """, startDate, endDate);

        List<SupplierPurchaseResponse> result = new ArrayList<>();
        for (var row : rows) {
            UUID supplierId = (UUID) row.get("supplier_id");
            BigDecimal totalPurchased = toBigDecimal(row.get("total_purchased"));
            int orderCount = ((Number) row.get("order_count")).intValue();

            String supplierName = thirdPartyRepository.findById(supplierId)
                    .map(tp -> tp.name())
                    .orElse("Desconocido");

            BigDecimal avgOrderValue = orderCount > 0
                    ? totalPurchased.divide(new BigDecimal(orderCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(new SupplierPurchaseResponse(supplierId, supplierName, totalPurchased, orderCount, avgOrderValue));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-product")
    public ResponseEntity<List<ProductPurchaseResponse>> byProduct(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        var rows = jdbc.queryForList("""
            SELECT
                li.product_id,
                COALESCE(SUM(li.ordered_qty), 0) AS total_qty,
                COALESCE(SUM(li.ordered_qty * li.unit_cost), 0) AS total_cost
            FROM purchase_line_items li
            JOIN purchase_orders po ON po.id = li.oc_id
            WHERE po.order_date >= ? AND po.order_date <= ?
            GROUP BY li.product_id
            ORDER BY total_cost DESC
            """, startDate, endDate);

        List<ProductPurchaseResponse> result = new ArrayList<>();
        for (var row : rows) {
            UUID productId = (UUID) row.get("product_id");
            BigDecimal totalQty = toBigDecimal(row.get("total_qty"));
            BigDecimal totalCost = toBigDecimal(row.get("total_cost"));

            var product = productRepository.findById(productId).orElse(null);
            String productName = product != null ? product.name() : "Desconocido";
            String productCode = product != null ? product.productCode() : "";

            BigDecimal avgUnitCost = totalQty.compareTo(BigDecimal.ZERO) > 0
                    ? totalCost.divide(totalQty, 4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.add(new ProductPurchaseResponse(productId, productName, productCode, totalQty, totalCost, avgUnitCost));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/comparison")
    public ResponseEntity<PurchaseSalesComparisonResponse> comparison(
            @RequestParam int year,
            @RequestParam int month
    ) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        // Total purchases: Σ(line.orderedQty × line.unitCost) for the month
        BigDecimal totalPurchases = jdbc.queryForObject("""
            SELECT COALESCE(SUM(li.ordered_qty * li.unit_cost), 0)
            FROM purchase_line_items li
            JOIN purchase_orders po ON po.id = li.oc_id
            WHERE po.order_date >= ? AND po.order_date <= ?
            """, BigDecimal.class, startDate, endDate);

        // Total sales: sum of total_amount for issued invoices
        BigDecimal totalSales = jdbc.queryForObject("""
            SELECT COALESCE(SUM(total_amount), 0)
            FROM sales_documents
            WHERE type = 'INVOICE'
              AND status = 'ISSUED'
              AND created_at >= ? AND created_at < ?
            """, BigDecimal.class,
            startDate.atStartOfDay(),
            endDate.plusDays(1).atStartOfDay());

        if (totalPurchases == null) totalPurchases = BigDecimal.ZERO;
        if (totalSales == null) totalSales = BigDecimal.ZERO;

        BigDecimal margin = totalSales.subtract(totalPurchases);
        BigDecimal marginPct = totalSales.compareTo(BigDecimal.ZERO) > 0
                ? margin.divide(totalSales, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return ResponseEntity.ok(new PurchaseSalesComparisonResponse(
                year, month, totalPurchases, totalSales, margin, marginPct
        ));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
