package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DashboardSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
public class DashboardController {

    private final JdbcTemplate jdbc;

    public DashboardController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/summary")
    public DashboardSummary getSummary() {
        BigDecimal todaySales = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(total_amount), 0)
            FROM sales_documents
            WHERE type = 'INVOICE'
              AND status = 'ISSUED'
              AND created_at >= CURRENT_DATE
              AND created_at < CURRENT_DATE + INTERVAL '1 day'
            """,
            BigDecimal.class
        );

        BigDecimal overdueReceivables = jdbc.queryForObject(
            "SELECT COALESCE(SUM(outstanding), 0) FROM accounts_receivable WHERE status = 'OVERDUE'",
            BigDecimal.class
        );

        Integer lowStockCount = jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT p.id)
            FROM products p
            JOIN inventory_stock s ON s.product_id = p.id
            WHERE s.current_quantity - s.committed_quantity <= p.min_stock
              AND p.min_stock > 0
            """,
            Integer.class
        );

        BigDecimal currentMonthMargin = jdbc.queryForObject(
            """
            SELECT
              COALESCE((
                SELECT SUM(total_net)
                FROM sales_documents
                WHERE type = 'INVOICE'
                  AND status = 'ISSUED'
                  AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', CURRENT_DATE)
              ), 0)
              -
              COALESCE((
                SELECT COALESCE(SUM(unit_cost * ABS(quantity)), 0)
                FROM inventory_movements
                WHERE movement_type IN ('EXIT', 'PRODUCTION_CONSUMPTION')
                  AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', CURRENT_DATE)
              ), 0)
            """,
            BigDecimal.class
        );

        return new DashboardSummary(
            todaySales,
            overdueReceivables,
            lowStockCount != null ? lowStockCount : 0,
            currentMonthMargin,
            LocalDateTime.now()
        );
    }
}
