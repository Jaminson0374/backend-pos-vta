package co.posinvent.integration;

import co.posinvent.application.usecase.CustomerBalanceUseCase;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.TestDataFactory;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards that the customer balance is derived exclusively from
 * {@code SUM(accounts_receivable.outstanding)} and never from the static
 * {@code third_parties.current_balance} column.
 *
 * <p>Seeds a client whose static balance is deliberately large and non-zero, with a credit limit of
 * 1000.00 and two AR rows whose outstanding adds up to 600.00. If any reader still mixed in the
 * static field, the derived balance would not match the AR sum.</p>
 *
 * <p>Deliberately NOT {@code @Transactional}: the fixtures commit their own transactions so the
 * rows are visible to the use case exactly as in production.</p>
 */
class CustomerBalanceDerivationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal STATIC_BALANCE = new BigDecimal("987654.32");
    private static final BigDecimal CREDIT_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal SECOND_CLIENT_CREDIT_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal NO_AR_CLIENT_CREDIT_LIMIT = new BigDecimal("500.00");

    @Autowired private CustomerBalanceUseCase customerBalanceUseCase;
    @Autowired private ThirdPartyRepository thirdPartyRepository;

    private UUID clientId;
    private UUID secondClientId;
    private UUID noArClientId;
    private UUID supplierId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var warehouseId = TestDataFactory.createWarehouse(
                    em, "Derived AR WH " + UUID.randomUUID().toString().substring(0, 8),
                    Warehouse.WarehouseType.CORTES);

            clientId = TestDataFactory.createClientThirdParty(
                    em,
                    "CLI-DERIVED-" + UUID.randomUUID().toString().substring(0, 8),
                    "Client Derived Balance",
                    CREDIT_LIMIT, STATIC_BALANCE);

            TestDataFactory.createAccountsReceivable(
                    em, clientId, warehouseId,
                    "AR-DERIVED-A-" + UUID.randomUUID().toString().substring(0, 6),
                    new BigDecimal("350.00"));
            TestDataFactory.createAccountsReceivable(
                    em, clientId, warehouseId,
                    "AR-DERIVED-B-" + UUID.randomUUID().toString().substring(0, 6),
                    new BigDecimal("250.00"));

            // A second client with its own AR ledger: the balance must still be filtered by clientId.
            secondClientId = TestDataFactory.createClientThirdParty(
                    em,
                    "CLI-DERIVED-2-" + UUID.randomUUID().toString().substring(0, 8),
                    "Second Client Derived Balance",
                    SECOND_CLIENT_CREDIT_LIMIT, BigDecimal.ZERO);
            TestDataFactory.createAccountsReceivable(
                    em, secondClientId, warehouseId,
                    "AR-DERIVED-C-" + UUID.randomUUID().toString().substring(0, 6),
                    new BigDecimal("900.00"));

            // A client with no AR rows at all: coalesce must yield 0 outstanding.
            noArClientId = TestDataFactory.createClientThirdParty(
                    em,
                    "CLI-DERIVED-3-" + UUID.randomUUID().toString().substring(0, 8),
                    "Client Without AR",
                    NO_AR_CLIENT_CREDIT_LIMIT, BigDecimal.ZERO);

            var supplier = new ThirdPartyEntity();
            supplier.setNumIdentification("SUP-DERIVED-" + UUID.randomUUID().toString().substring(0, 8));
            supplier.setName("Supplier Not A Client");
            supplier.setType(ThirdParty.ThirdPartyType.SUPPLIER);
            supplier.setPersonType(ThirdParty.PersonType.JURIDICA);
            supplier.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
            supplier.setTaxResponsibilities(List.of());
            supplier.setActive(true);
            supplier.setCreditLimit(BigDecimal.ZERO);
            supplier.setCurrentBalance(BigDecimal.ZERO);
            em.persist(supplier);
            supplierId = supplier.getId();
        });
    }

    @Test
    void outstandingComesFromTheArRowsAndIgnoresTheStaticCurrentBalance() {
        // Sanity: the static column really holds the large seeded value at assertion time.
        assertThat(thirdPartyRepository.findById(clientId).orElseThrow().currentBalance())
                .isEqualByComparingTo(STATIC_BALANCE);

        var balance = customerBalanceUseCase.getBalance(clientId);

        assertThat(balance.clientId()).isEqualTo(clientId);
        assertThat(balance.outstanding()).isEqualByComparingTo("600.00");
        assertThat(balance.creditLimit()).isEqualByComparingTo("1000.00");
        assertThat(balance.availableCredit()).isEqualByComparingTo("400.00");
        assertThat(balance.outstanding()).isNotEqualByComparingTo(STATIC_BALANCE);
    }

    @Test
    void balanceIsScopedToTheRequestedClient() {
        var second = customerBalanceUseCase.getBalance(secondClientId);

        assertThat(second.clientId()).isEqualTo(secondClientId);
        assertThat(second.outstanding()).isEqualByComparingTo("900.00");
        assertThat(second.creditLimit()).isEqualByComparingTo("1000.00");
        assertThat(second.availableCredit()).isEqualByComparingTo("100.00");

        // The second client's AR rows do not leak into the first client's balance.
        assertThat(customerBalanceUseCase.getBalance(clientId).outstanding())
                .isEqualByComparingTo("600.00");
    }

    @Test
    void clientWithoutArRowsHasZeroOutstandingAndFullCreditLimit() {
        var balance = customerBalanceUseCase.getBalance(noArClientId);

        assertThat(balance.clientId()).isEqualTo(noArClientId);
        assertThat(balance.outstanding()).isEqualByComparingTo("0.00");
        assertThat(balance.creditLimit()).isEqualByComparingTo("500.00");
        assertThat(balance.availableCredit()).isEqualByComparingTo("500.00");
    }

    @Test
    void aSupplierIdIsNotAClient() {
        assertThatThrownBy(() -> customerBalanceUseCase.getBalance(supplierId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cliente");
    }
}
