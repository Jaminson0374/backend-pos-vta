package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.IncomeStatementRow;
import co.posinvent.domain.model.ProfitabilityRow;
import co.posinvent.domain.model.SalesByPeriodRow;
import co.posinvent.domain.model.SalesByProductRow;
import co.posinvent.domain.repository.ReportRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
class ReportRepositoryAdapter implements ReportRepository {

    private final JdbcTemplate jdbc;

    ReportRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SalesByProductRow> salesByProduct(LocalDate from, LocalDate to, UUID warehouseId) {
        var sql = new StringBuilder("""
            SELECT p.id as productId, p.name as productName, p.product_code as productCode,
                   pg.name as productGroup,
                   COALESCE(SUM(si.quantity), 0) as totalQuantity,
                   COALESCE(SUM(si.subtotal), 0) as totalRevenue,
                   COUNT(DISTINCT sd.id) as transactionCount
            FROM sales_documents sd
            JOIN sales_items si ON si.document_id = sd.id
            JOIN products p ON p.id = si.product_id
            LEFT JOIN product_groups pg ON pg.id = p.product_group_id
            WHERE sd.type = 'INVOICE' AND sd.status = 'ISSUED'
              AND sd.created_at >= ? AND sd.created_at < ?
            """);

        var params = new ArrayList<>();
        Timestamp tsFrom = from != null ? Timestamp.valueOf(from.atStartOfDay()) : Timestamp.valueOf("2000-01-01 00:00:00");
        Timestamp tsTo = to != null ? Timestamp.valueOf(to.plusDays(1).atStartOfDay()) : Timestamp.valueOf("2100-01-01 00:00:00");
        params.add(tsFrom);
        params.add(tsTo);

        if (warehouseId != null) {
            sql.append("AND sd.warehouse_id = ?::uuid ");
            params.add(warehouseId.toString());
        }

        sql.append("""
            GROUP BY p.id, p.name, p.product_code, pg.name
            ORDER BY totalRevenue DESC
            """);

        return jdbc.query(sql.toString(), (rs, rowNum) -> new SalesByProductRow(
                UUID.fromString(rs.getString("productId")),
                rs.getString("productName"),
                rs.getString("productCode"),
                rs.getString("productGroup"),
                rs.getBigDecimal("totalQuantity"),
                rs.getBigDecimal("totalRevenue"),
                rs.getInt("transactionCount")
        ), params.toArray());
    }

    @Override
    public List<SalesByPeriodRow> salesByPeriod(LocalDate from, LocalDate to, String granularity) {
        String truncUnit = switch (granularity != null ? granularity : "DAILY") {
            case "DAILY" -> "day";
            case "WEEKLY" -> "week";
            case "MONTHLY" -> "month";
            default -> "day";
        };

        String sql = "SELECT TO_CHAR(DATE_TRUNC('" + truncUnit + "', sd.created_at), 'YYYY-MM-DD') as period, "
                   + "COUNT(sd.id) as totalInvoices, "
                   + "COALESCE(SUM(sd.total_amount), 0) as totalRevenue, "
                   + "COALESCE(SUM(sd.total_net), 0) as totalNet, "
                   + "COALESCE(SUM(sd.total_tax_0 + sd.total_tax_5 + sd.total_tax_19), 0) as totalTax "
                   + "FROM sales_documents sd "
                   + "WHERE sd.type = 'INVOICE' AND sd.status = 'ISSUED' "
                   + "AND sd.created_at >= ? AND sd.created_at < ? "
                   + "GROUP BY DATE_TRUNC('" + truncUnit + "', sd.created_at) "
                   + "ORDER BY period";

        Timestamp tsFrom = from != null ? Timestamp.valueOf(from.atStartOfDay()) : Timestamp.valueOf("2000-01-01 00:00:00");
        Timestamp tsTo = to != null ? Timestamp.valueOf(to.plusDays(1).atStartOfDay()) : Timestamp.valueOf("2100-01-01 00:00:00");

        return jdbc.query(sql, (rs, rowNum) -> new SalesByPeriodRow(
                rs.getString("period"),
                rs.getInt("totalInvoices"),
                rs.getBigDecimal("totalRevenue"),
                rs.getBigDecimal("totalNet"),
                rs.getBigDecimal("totalTax")
        ), tsFrom, tsTo);
    }

    @Override
    public List<ProfitabilityRow> profitability(LocalDate from, LocalDate to, UUID warehouseId) {
        Timestamp tsFrom = from != null ? Timestamp.valueOf(from.atStartOfDay()) : Timestamp.valueOf("2000-01-01 00:00:00");
        Timestamp tsTo = to != null ? Timestamp.valueOf(to.plusDays(1).atStartOfDay()) : Timestamp.valueOf("2100-01-01 00:00:00");

        String revWh = warehouseId != null ? "AND sd.warehouse_id = ?::uuid " : "";
        String cogsWh = warehouseId != null ? "AND im.warehouse_id = ?::uuid " : "";

        String sql = """
            SELECT p.id as productId, p.name as productName, p.product_code as productCode,
                   COALESCE(rev.totalRevenue, 0) as totalRevenue,
                   COALESCE(cogs.totalCogs, 0) as totalCogs,
                   COALESCE(rev.totalRevenue, 0) - COALESCE(cogs.totalCogs, 0) as grossMargin,
                   CASE WHEN COALESCE(rev.totalRevenue, 0) > 0
                        THEN ROUND(((COALESCE(rev.totalRevenue, 0) - COALESCE(cogs.totalCogs, 0)) /
                              COALESCE(rev.totalRevenue, 0) * 100)::numeric, 2)
                        ELSE 0 END as marginPercent
            FROM products p
            LEFT JOIN (
                SELECT si.product_id, SUM(si.subtotal) as totalRevenue
                FROM sales_items si
                JOIN sales_documents sd ON sd.id = si.document_id
                WHERE sd.type = 'INVOICE' AND sd.status = 'ISSUED'
                  AND sd.created_at >= ? AND sd.created_at < ?
                  $revWhClause$
                GROUP BY si.product_id
            ) rev ON rev.product_id = p.id
            LEFT JOIN (
                SELECT im.product_id,
                       SUM(im.quantity * im.unit_cost) as totalCogs
                FROM inventory_movements im
                WHERE im.movement_type IN ('EXIT', 'PRODUCTION_CONSUMPTION')
                  AND im.created_at >= ? AND im.created_at < ?
                  $cogsWhClause$
                GROUP BY im.product_id
            ) cogs ON cogs.product_id = p.id
            WHERE (rev.totalRevenue IS NOT NULL AND rev.totalRevenue > 0)
               OR (cogs.totalCogs IS NOT NULL AND cogs.totalCogs > 0)
            ORDER BY grossMargin DESC
            """
            .replace("$revWhClause$", revWh)
            .replace("$cogsWhClause$", cogsWh);

        var params = new ArrayList<>();
        params.add(tsFrom);
        params.add(tsTo);
        if (warehouseId != null) params.add(warehouseId.toString());
        params.add(tsFrom);
        params.add(tsTo);
        if (warehouseId != null) params.add(warehouseId.toString());

        return jdbc.query(sql, (rs, rowNum) -> new ProfitabilityRow(
                UUID.fromString(rs.getString("productId")),
                rs.getString("productName"),
                rs.getString("productCode"),
                rs.getBigDecimal("totalRevenue"),
                rs.getBigDecimal("totalCogs"),
                rs.getBigDecimal("grossMargin"),
                rs.getBigDecimal("marginPercent")
        ), params.toArray());
    }

    @Override
    public IncomeStatementRow incomeStatement(LocalDate from, LocalDate to) {
        Timestamp tsFrom = from != null ? Timestamp.valueOf(from.atStartOfDay()) : Timestamp.valueOf("2000-01-01 00:00:00");
        Timestamp tsTo = to != null ? Timestamp.valueOf(to.plusDays(1).atStartOfDay()) : Timestamp.valueOf("2100-01-01 00:00:00");

        // Revenue: INVOICE - CREDIT_NOTE
        var revenue = jdbc.queryForObject("""
            SELECT COALESCE(SUM(CASE WHEN sd.type = 'INVOICE' AND sd.status = 'ISSUED' THEN sd.total_amount ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN sd.type = 'CREDIT_NOTE' AND sd.status = 'ISSUED' THEN sd.total_amount ELSE 0 END), 0)
            FROM sales_documents sd
            WHERE sd.created_at >= ? AND sd.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (revenue == null) revenue = BigDecimal.ZERO;

        // Revenue detail
        var invoiceRev = jdbc.queryForObject("""
            SELECT COALESCE(SUM(sd.total_amount), 0)
            FROM sales_documents sd
            WHERE sd.type = 'INVOICE' AND sd.status = 'ISSUED'
              AND sd.created_at >= ? AND sd.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (invoiceRev == null) invoiceRev = BigDecimal.ZERO;

        var creditNoteRev = jdbc.queryForObject("""
            SELECT COALESCE(SUM(sd.total_amount), 0)
            FROM sales_documents sd
            WHERE sd.type = 'CREDIT_NOTE' AND sd.status = 'ISSUED'
              AND sd.created_at >= ? AND sd.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (creditNoteRev == null) creditNoteRev = BigDecimal.ZERO;

        // COGS from kardex EXIT + PRODUCTION_CONSUMPTION
        var cogs = jdbc.queryForObject("""
            SELECT COALESCE(SUM(im.quantity * im.unit_cost), 0)
            FROM inventory_movements im
            WHERE im.movement_type IN ('EXIT', 'PRODUCTION_CONSUMPTION')
              AND im.created_at >= ? AND im.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (cogs == null) cogs = BigDecimal.ZERO;

        // COGS detail
        var exitCogs = jdbc.queryForObject("""
            SELECT COALESCE(SUM(im.quantity * im.unit_cost), 0)
            FROM inventory_movements im
            WHERE im.movement_type = 'EXIT'
              AND im.created_at >= ? AND im.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (exitCogs == null) exitCogs = BigDecimal.ZERO;

        var productionCogs = jdbc.queryForObject("""
            SELECT COALESCE(SUM(im.quantity * im.unit_cost), 0)
            FROM inventory_movements im
            WHERE im.movement_type = 'PRODUCTION_CONSUMPTION'
              AND im.created_at >= ? AND im.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (productionCogs == null) productionCogs = BigDecimal.ZERO;

        // Expenses: supplier_invoices + production overheads
        var supplierExpenses = jdbc.queryForObject("""
            SELECT COALESCE(SUM(si.total), 0)
            FROM supplier_invoices si
            WHERE si.created_at >= ? AND si.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (supplierExpenses == null) supplierExpenses = BigDecimal.ZERO;

        var productionOverhead = jdbc.queryForObject("""
            SELECT COALESCE(SUM(pb.overhead_cost + pb.direct_labor_cost), 0)
            FROM production_batches pb
            WHERE pb.created_at >= ? AND pb.created_at < ?
            """, BigDecimal.class, tsFrom, tsTo);
        if (productionOverhead == null) productionOverhead = BigDecimal.ZERO;

        var totalExpenses = supplierExpenses.add(productionOverhead);

        var grossMargin = revenue.subtract(cogs);
        var netIncome = grossMargin.subtract(totalExpenses);

        var revenueDetails = List.of(
                new IncomeStatementRow.RevenueItem("Ingresos por facturación", invoiceRev),
                new IncomeStatementRow.RevenueItem("Notas crédito", creditNoteRev.negate())
        );

        var cogsDetails = List.of(
                new IncomeStatementRow.RevenueItem("Costo de ventas (salidas)", exitCogs),
                new IncomeStatementRow.RevenueItem("Consumo en producción", productionCogs)
        );

        var expenseDetails = List.of(
                new IncomeStatementRow.RevenueItem("Facturas proveedores", supplierExpenses),
                new IncomeStatementRow.RevenueItem("Costos indirectos producción", productionOverhead)
        );

        return new IncomeStatementRow(
                revenue, cogs, grossMargin, totalExpenses, netIncome,
                revenueDetails, cogsDetails, expenseDetails
        );
    }
}
