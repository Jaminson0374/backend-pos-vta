package co.posinvent.application.usecase;

import co.posinvent.application.dto.CustomerBalanceResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Reads the derived customer receivable balance.
 *
 * <p>{@code outstanding} comes from {@code SUM(accounts_receivable.outstanding)} for the client,
 * and {@code availableCredit = creditLimit - outstanding}. The balance is derived only from the
 * AR ledger; the legacy static balance column was removed in A6.</p>
 */
@Service
public class CustomerBalanceUseCase {

    private final ThirdPartyRepository thirdPartyRepository;
    private final AccountsReceivableRepository accountsReceivableRepository;

    public CustomerBalanceUseCase(
            ThirdPartyRepository thirdPartyRepository,
            AccountsReceivableRepository accountsReceivableRepository
    ) {
        this.thirdPartyRepository = thirdPartyRepository;
        this.accountsReceivableRepository = accountsReceivableRepository;
    }

    /**
     * Builds the customer balance:
     * {@code outstanding = SUM(accounts_receivable.outstanding)} and
     * {@code availableCredit = creditLimit - outstanding}.
     *
     * <p>Only {@code CLIENT} and {@code BOTH} third parties are clients; any other type (for
     * example a {@code SUPPLIER} or {@code EMPLOYEE} id) is reported as not found.</p>
     *
     * @param clientId the client to report
     * @return the normalized read model (nulls become {@link BigDecimal#ZERO})
     * @throws ResourceNotFoundException when the third party does not exist or is not a client
     */
    @Transactional(readOnly = true)
    public CustomerBalanceResponse getBalance(UUID clientId) {
        var client = thirdPartyRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", clientId));

        if (client.type() != ThirdParty.ThirdPartyType.CLIENT
                && client.type() != ThirdParty.ThirdPartyType.BOTH) {
            throw new ResourceNotFoundException("Cliente", clientId);
        }

        var outstanding = zeroIfNull(accountsReceivableRepository.sumOutstandingByClientId(clientId));
        var creditLimit = zeroIfNull(client.creditLimit());
        var availableCredit = creditLimit.subtract(outstanding);

        return new CustomerBalanceResponse(clientId, outstanding, creditLimit, availableCredit);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
