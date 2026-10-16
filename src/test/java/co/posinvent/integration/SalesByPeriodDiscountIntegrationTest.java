package co.posinvent.integration;

import co.posinvent.application.usecase.ReportUseCase;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.SalesDocumentEntity;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the sales-by-period report exposes the discount granted (WU-30 / Task B).
 *
 * <p>Seeds a single ISSUED invoice carrying {@code discount_total = 1,450} (a 500 line
 * discount plus a 950 prorated document discount) on a unique period, then checks the
 * report aggregates that column — not just revenue and tax.</p>
 */
class SalesByPeriodDiscountIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate SEED_PERIOD = LocalDate.of(2099, 6, 15);

    @Autowired private ReportUseCase reportUseCase;

    @Test
    void salesByPeriod_exposesTotalDiscountAggregatedFromDiscountTotal() {
        seedIssuedInvoiceWithDiscount(new BigDecimal("1450.00"));

        var rows = reportUseCase.salesByPeriod(
                SEED_PERIOD.minusDays(1), SEED_PERIOD, "DAILY");

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.period()).isEqualTo("2099-06-15");
        assertThat(row.totalInvoices()).isEqualTo(1);
        assertThat(row.totalDiscount()).isEqualByComparingTo("1450.00");
        assertThat(row.totalRevenue()).isEqualByComparingTo("10174.50");
        assertThat(row.totalNet()).isEqualByComparingTo("8550.00");
        assertThat(row.totalTax()).isEqualByComparingTo("1624.50");
    }

    private void seedIssuedInvoiceWithDiscount(BigDecimal discountTotal) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "SRD", Warehouse.WarehouseType.CORTES);
            var clientId = TestDataFactory.createClientThirdParty(
                    em, "SRD-" + UUID.randomUUID().toString().substring(0, 8), "Period Discount Client");

            var doc = new SalesDocumentEntity();
            doc.setType(SalesDocumentType.INVOICE);
            doc.setStatus(SalesDocumentStatus.ISSUED);
            doc.setDocumentNumber("SRD-" + UUID.randomUUID().toString().substring(0, 8));
            doc.setClientId(clientId);
            doc.setWarehouseId(warehouseId);
            doc.setTotalNet(new BigDecimal("8550.00"));
            doc.setTotalTax0(BigDecimal.ZERO);
            doc.setTotalTax5(BigDecimal.ZERO);
            doc.setTotalTax19(new BigDecimal("1624.50"));
            doc.setTotalAmount(new BigDecimal("10174.50"));
            doc.setCreditSale(false);
            doc.setDiscountTotal(discountTotal);
            doc.setDocumentDiscount(new BigDecimal("950.00"));
            doc.setDocumentDiscountType("PCT");
            doc.setDocumentDiscountValue(new BigDecimal("10.00"));
            em.persist(doc);
            em.flush();

            // created_at is @CreatedDate-managed, so pin it to a unique period via native SQL
            // (Hibernate leaves the updatable=false column out of entity updates).
            em.createNativeQuery("UPDATE sales_documents SET created_at = :ts WHERE id = :id")
                    .setParameter("ts", OffsetDateTime.of(2099, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC))
                    .setParameter("id", doc.getId())
                    .executeUpdate();
        });
    }
}
