package co.posinvent.application.usecase;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.PaymentRepository;
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
 * Unit contract for the supplier net position (S3a):
 * {@code netPosition = payableOutstanding - advanceBalance}.
 *
 * <p>An unapplied advance is an asset (saldo a favor); when it exceeds the CxP the net position is
 * negative, meaning we have credit in our favor. The legacy static balance column was removed in
 * A6 and must not participate.</p>
 */
@ExtendWith(MockitoExtension.class)
class SupplierBalanceUseCaseTest {

    private static final UUID SUPPLIER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ThirdPartyRepository thirdPartyRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AccountsPayableUseCase accountsPayableUseCase;

    private SupplierBalanceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SupplierBalanceUseCase(thirdPartyRepository, paymentRepository, accountsPayableUseCase);
    }

    @Test
    @DisplayName("payable 600 + advance 1000 -> netPosition -400 (credit in our favor)")
    void advanceGreaterThanPayableProducesNegativeNetPosition() {
        when(thirdPartyRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier()));
        when(accountsPayableUseCase.supplierOutstanding(SUPPLIER_ID))
                .thenReturn(new BigDecimal("600.00"));
        when(paymentRepository.sumRemainingAdvanceBySupplierId(SUPPLIER_ID))
                .thenReturn(new BigDecimal("1000.00"));

        var response = useCase.getBalance(SUPPLIER_ID);

        assertThat(response.supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(response.payableOutstanding()).isEqualByComparingTo("600.00");
        assertThat(response.advanceBalance()).isEqualByComparingTo("1000.00");
        assertThat(response.netPosition()).isEqualByComparingTo("-400.00");
    }

    @Test
    @DisplayName("a null advance sum is normalized to zero")
    void nullAdvanceSumIsTreatedAsZero() {
        when(thirdPartyRepository.findById(SUPPLIER_ID)).thenReturn(Optional.of(supplier()));
        when(accountsPayableUseCase.supplierOutstanding(SUPPLIER_ID))
                .thenReturn(new BigDecimal("250.00"));
        when(paymentRepository.sumRemainingAdvanceBySupplierId(SUPPLIER_ID)).thenReturn(null);

        var response = useCase.getBalance(SUPPLIER_ID);

        assertThat(response.advanceBalance()).isEqualByComparingTo("0");
        assertThat(response.netPosition()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("a missing supplier raises ResourceNotFoundException and never reads balances")
    void missingSupplierThrowsResourceNotFoundException() {
        when(thirdPartyRepository.findById(SUPPLIER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getBalance(SUPPLIER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Proveedor");

        verify(accountsPayableUseCase, never()).supplierOutstanding(any());
        verify(paymentRepository, never()).sumRemainingAdvanceBySupplierId(any());
    }

    private ThirdParty supplier() {
        return new ThirdParty(
                SUPPLIER_ID, "900123456", "Proveedor Test", ThirdParty.ThirdPartyType.SUPPLIER,
                null, BigDecimal.ZERO,
                ThirdParty.PersonType.JURIDICA, ThirdParty.TaxRegime.ORDINARIO,
                List.of(), "001", null, true, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, 0, null, null, null, null, null, null, null,
                null, false, false, false, false, false, null);
    }
}
