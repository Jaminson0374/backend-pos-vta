package co.posinvent.integration;

import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.infrastructure.adapters.out.persistence.PurchaseOrderEntity;
import co.posinvent.infrastructure.adapters.out.persistence.SupplierInvoiceEntity;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that {@link SupplierInvoiceRepository#findById(UUID)} returns a fully loaded domain
 * invoice, including its lazy {@code ocIds} element collection, when no surrounding transaction
 * exists.
 *
 * <p>The mapper runs after the repository's own transaction ends, so without a transactional
 * boundary on the adapter the lazy collection blows up with {@code LazyInitializationException}.
 * The fixture is committed through a {@link TransactionTemplate}; the test method is deliberately
 * NOT {@code @Transactional}.</p>
 */
class SupplierInvoiceFindByIdOcIdsIntegrationTest extends AbstractIntegrationTest {

    @Autowired private SupplierInvoiceRepository supplierInvoiceRepository;

    @Test
    void findByIdLoadsOcIdsOutsideAnySurroundingTransaction() {
        var invoiceId = UUID.randomUUID();
        var ocIdHolder = new UUID[1];

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-OCIDS-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier OC Ids");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);

            var order = new PurchaseOrderEntity();
            order.setSupplierId(supplier.getId());
            order.setStatus(PurchaseOrderStatus.PENDING);
            order.setOrderDate(LocalDate.now());
            order.setCurrency("COP");
            order.setCreatedBy(TEST_USER_ID);
            em.persist(order);
            em.flush();
            ocIdHolder[0] = order.getId();

            var invoice = new SupplierInvoiceEntity();
            invoice.setId(invoiceId);
            invoice.setSupplierId(supplier.getId());
            invoice.setInvoiceNumber("FAC-OCIDS-" + UUID.randomUUID().toString().substring(0, 8));
            invoice.setIssueDate(LocalDate.now());
            invoice.setDueDate(LocalDate.now().plusDays(30));
            invoice.setSubtotal(new BigDecimal("100.00"));
            invoice.setIvaTotal(BigDecimal.ZERO);
            invoice.setRetentionTotal(BigDecimal.ZERO);
            invoice.setTotal(new BigDecimal("100.00"));
            invoice.setStatus(InvoiceStatus.PENDING);
            invoice.setCreatedBy(TEST_USER_ID);
            invoice.setOcIds(new ArrayList<>(List.of(ocIdHolder[0])));
            em.persist(invoice);
            em.flush();
        });

        SupplierInvoice loaded = supplierInvoiceRepository.findById(invoiceId).orElseThrow();

        assertThat(loaded.ocIds()).containsExactly(ocIdHolder[0]);
    }
}
