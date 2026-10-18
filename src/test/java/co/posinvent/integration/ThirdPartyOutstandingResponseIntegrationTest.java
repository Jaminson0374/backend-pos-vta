package co.posinvent.integration;

import co.posinvent.application.dto.ThirdPartyResponse;
import co.posinvent.application.usecase.ThirdPartyUseCase;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards that the third-party API exposes the derived receivable balance
 * ({@code SUM(accounts_receivable.outstanding)}) as {@code outstanding}. The legacy static
 * balance column was removed in A6.
 *
 * <p>Covers the single-read path ({@code getById}), the batch aggregate used by the clients list
 * and search ({@code listByTypeAndActive}, {@code search}), and the non-client rule where
 * {@code outstanding} must be {@code null}.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: fixtures commit their own transaction so the use
 * case reads them exactly as in production.</p>
 */
class ThirdPartyOutstandingResponseIntegrationTest extends AbstractIntegrationTest {

    private static final String SEARCH_PREFIX = "ZZ Outstanding";
    private static final BigDecimal CREDIT_LIMIT = new BigDecimal("1000.00");

    @Autowired private ThirdPartyUseCase thirdPartyUseCase;

    private UUID clientAId;
    private UUID clientBId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "ZZ Outstanding WH " + suffix, Warehouse.WarehouseType.CORTES);

            clientAId = TestDataFactory.createClientThirdParty(
                    em, "ZZ-OUT-A-" + suffix, SEARCH_PREFIX + " Client A", CREDIT_LIMIT);
            TestDataFactory.createAccountsReceivable(
                    em, clientAId, warehouseId, "ZZ-OUT-AR-A-" + suffix, new BigDecimal("350.00"));
            TestDataFactory.createAccountsReceivable(
                    em, clientAId, warehouseId, "ZZ-OUT-AR-B-" + suffix, new BigDecimal("250.00"));

            clientBId = TestDataFactory.createClientThirdParty(
                    em, "ZZ-OUT-B-" + suffix, SEARCH_PREFIX + " Client B", CREDIT_LIMIT);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("ZZ-OUT-SUP-" + suffix);
            supplier.setName(SEARCH_PREFIX + " Supplier");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();
        });
    }

    @Test
    void outstandingIsDerivedFromArForClientsAndNullForNonClients() {
        // getById: single-row read derives the AR sum.
        assertThat(thirdPartyUseCase.getById(clientAId).outstanding())
                .isEqualByComparingTo("600.00");
        assertThat(thirdPartyUseCase.getById(clientBId).outstanding())
                .isEqualByComparingTo("0.00");
        assertThat(thirdPartyUseCase.getById(supplierId).outstanding())
                .isNull();

        // search: the batch aggregate must resolve both client rows in one query.
        var searchRows = thirdPartyUseCase.search(SEARCH_PREFIX, PageRequest.of(0, 10)).content();
        assertThat(findById(searchRows, clientAId).outstanding()).isEqualByComparingTo("600.00");
        assertThat(findById(searchRows, clientBId).outstanding()).isEqualByComparingTo("0.00");

        // Non-client rows (the supplier) never carry a derived outstanding balance.
        assertThat(searchRows)
                .allSatisfy(row -> {
                    if (row.type() != ThirdParty.ThirdPartyType.CLIENT
                            && row.type() != ThirdParty.ThirdPartyType.BOTH) {
                        assertThat(row.outstanding()).isNull();
                    }
                });
        assertThat(findById(searchRows, supplierId).outstanding()).isNull();

        // Clients list path: same batch aggregate over CLIENT/active rows.
        var clientRows = thirdPartyUseCase
                .listByTypeAndActive(ThirdParty.ThirdPartyType.CLIENT, true, PageRequest.of(0, 200))
                .content();
        assertThat(findById(clientRows, clientAId).outstanding()).isEqualByComparingTo("600.00");
    }

    private static ThirdPartyResponse findById(List<ThirdPartyResponse> rows, UUID id) {
        return rows.stream()
                .filter(row -> row.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected row for id " + id));
    }
}
