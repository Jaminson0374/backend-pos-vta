package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit contract for the derived customer balance (A1):
 * {@code outstanding = SUM(accounts_receivable.outstanding)},
 * {@code availableCredit = creditLimit - outstanding}.
 *
 * <p>The static {@code third_parties.current_balance} column must not participate in the result.
 * A client may be over the limit, so {@code availableCredit} is allowed to be negative and is never
 * clamped.</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerBalanceUseCaseTest {

    private static final UUID CLIENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final BigDecimal STATIC_BALANCE = new BigDecimal("987654.32");

    @Mock
    private ThirdPartyRepository thirdPartyRepository;

    @Mock
    private AccountsReceivableRepository accountsReceivableRepository;

    private CustomerBalanceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CustomerBalanceUseCase(thirdPartyRepository, accountsReceivableRepository);
    }

    @Test
    @DisplayName("outstanding comes from the AR sum and ignores the static current_balance")
    void derivesOutstandingFromArSum() {
        when(thirdPartyRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(thirdParty(ThirdParty.ThirdPartyType.CLIENT,
                        new BigDecimal("1000.00"), STATIC_BALANCE)));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(new BigDecimal("600.00"));

        var response = useCase.getBalance(CLIENT_ID);

        assertThat(response.clientId()).isEqualTo(CLIENT_ID);
        assertThat(response.outstanding()).isEqualByComparingTo("600.00");
        assertThat(response.creditLimit()).isEqualByComparingTo("1000.00");
        assertThat(response.availableCredit()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("a BOTH third party is a valid client")
    void bothTypeIsAccepted() {
        when(thirdPartyRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(thirdParty(ThirdParty.ThirdPartyType.BOTH,
                        new BigDecimal("1000.00"), BigDecimal.ZERO)));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(new BigDecimal("250.00"));

        var response = useCase.getBalance(CLIENT_ID);

        assertThat(response.outstanding()).isEqualByComparingTo("250.00");
        assertThat(response.availableCredit()).isEqualByComparingTo("750.00");
    }

    @Test
    @DisplayName("a null AR sum and a null credit limit are normalized to zero")
    void nullSumAndNullCreditLimitBecomeZero() {
        when(thirdPartyRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(thirdParty(ThirdParty.ThirdPartyType.CLIENT, null, null)));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID)).thenReturn(null);

        var response = useCase.getBalance(CLIENT_ID);

        assertThat(response.outstanding()).isEqualByComparingTo("0");
        assertThat(response.creditLimit()).isEqualByComparingTo("0");
        assertThat(response.availableCredit()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("availableCredit is negative when outstanding exceeds the credit limit")
    void availableCreditCanBeNegative() {
        when(thirdPartyRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(thirdParty(ThirdParty.ThirdPartyType.CLIENT,
                        new BigDecimal("1000.00"), BigDecimal.ZERO)));
        when(accountsReceivableRepository.sumOutstandingByClientId(CLIENT_ID))
                .thenReturn(new BigDecimal("1500.00"));

        var response = useCase.getBalance(CLIENT_ID);

        assertThat(response.outstanding()).isEqualByComparingTo("1500.00");
        assertThat(response.availableCredit()).isEqualByComparingTo("-500.00");
    }

    @Test
    @DisplayName("a missing third party raises ResourceNotFoundException and never reads AR")
    void missingClientThrowsResourceNotFoundException() {
        when(thirdPartyRepository.findById(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getBalance(CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cliente");

        verify(accountsReceivableRepository, never()).sumOutstandingByClientId(any());
    }

    @Test
    @DisplayName("a SUPPLIER id is not a client and raises ResourceNotFoundException")
    void supplierTypeThrowsResourceNotFoundException() {
        when(thirdPartyRepository.findById(CLIENT_ID))
                .thenReturn(Optional.of(thirdParty(ThirdParty.ThirdPartyType.SUPPLIER,
                        new BigDecimal("1000.00"), BigDecimal.ZERO)));

        assertThatThrownBy(() -> useCase.getBalance(CLIENT_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Cliente");

        verify(accountsReceivableRepository, never()).sumOutstandingByClientId(any());
    }

    private ThirdParty thirdParty(ThirdParty.ThirdPartyType type,
                                  BigDecimal creditLimit, BigDecimal currentBalance) {
        return new ThirdParty(
                CLIENT_ID, "900123456", "Tercero Test", type,
                null, creditLimit, currentBalance,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }
}
