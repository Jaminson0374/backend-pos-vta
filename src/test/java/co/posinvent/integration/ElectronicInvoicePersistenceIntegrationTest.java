package co.posinvent.integration;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.ElectronicInvoice;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the {@code electronic_invoices.provider_response} jsonb binding.
 *
 * <p>The column is declared {@code JSONB} in V65 but the entity mapped it as a plain
 * {@code String} without {@code @JdbcTypeCode(SqlTypes.JSON)}. Hibernate therefore bound
 * the value as {@code varchar} and Postgres rejected the insert with SQLState 42804
 * ({@code column "provider_response" is of type jsonb but expression is of type character varying}).</p>
 *
 * <p>This test persists a non-empty {@code Map<String,Object>} as the provider response and reads
 * it back, asserting the map round-trips through the jsonb column.</p>
 */
class ElectronicInvoicePersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ElectronicInvoiceRepository invoiceRepo;
    @Autowired private ThirdPartyRepository thirdPartyRepo;

    private UUID salesDocumentId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "EINV", Warehouse.WarehouseType.CORTES);
            var productId = TestDataFactory.createProduct(
                    em, "TEST-EINV", "Electronic Invoice", false, true);
            em.flush();

            var clientId = TestDataFactory.createClientThirdParty(
                    em, "EI-" + UUID.randomUUID().toString().substring(0, 8), "Einvoice Client");

            var systemSupplier = thirdPartyRepo.findByNumIdentification("000000000-0").orElseThrow();
            var batchId = TestDataFactory.createBatch(
                    em, productId, systemSupplier.id(), warehouseId,
                    new BigDecimal("10"), Batch.BatchStatus.OPEN);

            salesDocumentId = TestDataFactory.createConfirmedOrder(
                    em, "ORD-EINV-" + UUID.randomUUID().toString().substring(0, 6),
                    clientId, warehouseId, productId, batchId, BigDecimal.ONE);
            em.flush();
        });
    }

    @Test
    void providerResponseJsonbRoundTripsAsMap() {
        var providerResponse = Map.<String, Object>of(
                "status", "ACCEPTED_BY_DIAN",
                "reference", "PROVIDER-REF-1",
                "attempt", 3);

        var saved = invoiceRepo.save(new ElectronicInvoice(
                null,
                salesDocumentId,
                null,
                "CUFE-JSONB-1",
                "QR-1",
                providerResponse,
                ElectronicInvoiceStatus.ACCEPTED_BY_DIAN,
                null,
                null,
                null));

        assertThat(saved.id()).isNotNull();

        var reloaded = invoiceRepo.findById(saved.id()).orElseThrow();
        assertThat(reloaded.providerResponse()).isNotNull();
        assertThat(reloaded.providerResponse())
                .containsEntry("status", "ACCEPTED_BY_DIAN")
                .containsEntry("reference", "PROVIDER-REF-1");
        assertThat(reloaded.providerResponse().get("attempt")).isEqualTo(3);
    }
}
