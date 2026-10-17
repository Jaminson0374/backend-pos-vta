package co.posinvent.application.usecase;

import co.posinvent.application.dto.SupplierBalanceResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.PaymentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads the supplier net position.
 *
 * <p>The payable side reuses {@link AccountsPayableUseCase#supplierOutstanding(UUID)} (no duplicated
 * CxP query). The asset side is the sum of the supplier's unapplied advances. The static
 * {@code third_parties.current_balance} is AR-only and is intentionally not consulted.</p>
 */
@Service
public class SupplierBalanceUseCase {

    private final ThirdPartyRepository thirdPartyRepository;
    private final PaymentRepository paymentRepository;
    private final AccountsPayableUseCase accountsPayableUseCase;

    public SupplierBalanceUseCase(
            ThirdPartyRepository thirdPartyRepository,
            PaymentRepository paymentRepository,
            AccountsPayableUseCase accountsPayableUseCase
    ) {
        this.thirdPartyRepository = thirdPartyRepository;
        this.paymentRepository = paymentRepository;
        this.accountsPayableUseCase = accountsPayableUseCase;
    }

    /**
     * Builds the supplier net position:
     * {@code netPosition = payableOutstanding - advanceBalance}.
     *
     * @param supplierId the supplier to report
     * @return the normalized read model (nulls become {@link BigDecimal#ZERO})
     * @throws ResourceNotFoundException when the supplier does not exist
     */
    @Transactional(readOnly = true)
    public SupplierBalanceResponse getBalance(UUID supplierId) {
        thirdPartyRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", supplierId));

        var payableOutstanding = zeroIfNull(accountsPayableUseCase.supplierOutstanding(supplierId));
        var advanceBalance = zeroIfNull(paymentRepository.sumRemainingAdvanceBySupplierId(supplierId));
        var netPosition = payableOutstanding.subtract(advanceBalance);

        return new SupplierBalanceResponse(
                supplierId, payableOutstanding, advanceBalance, netPosition);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
