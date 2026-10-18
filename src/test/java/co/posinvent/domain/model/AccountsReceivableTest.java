package co.posinvent.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boundary contract for the receivable status/outstanding derivation (work unit A4,
 * R3-status-boundary-test).
 *
 * <p>A fully returned receivable has {@code totalAmount = 0}, so the fully-covered check must run
 * before the "nothing paid" branch; otherwise it would stay OPEN forever even though nothing is
 * owed.</p>
 */
class AccountsReceivableTest {

    @Test
    void zeroTotalWithZeroPaidIsPaidBecauseNothingIsOwed() {
        assertThat(AccountsReceivable.computeStatus(new BigDecimal("0"), new BigDecimal("0")))
                .isEqualTo(AccountsReceivable.ArStatus.PAID);
    }

    @Test
    void positiveTotalWithZeroPaidIsOpen() {
        assertThat(AccountsReceivable.computeStatus(new BigDecimal("100.00"), new BigDecimal("0")))
                .isEqualTo(AccountsReceivable.ArStatus.OPEN);
    }

    @Test
    void partiallyPaidReceivableIsPartial() {
        assertThat(AccountsReceivable.computeStatus(
                        new BigDecimal("100.00"), new BigDecimal("40.00")))
                .isEqualTo(AccountsReceivable.ArStatus.PARTIAL);
    }

    @Test
    void fullyPaidReceivableIsPaid() {
        assertThat(AccountsReceivable.computeStatus(
                        new BigDecimal("100.00"), new BigDecimal("100.00")))
                .isEqualTo(AccountsReceivable.ArStatus.PAID);
    }

    @Test
    void computeOutstandingSubtractsPaidAmountFromTotal() {
        assertThat(AccountsReceivable.computeOutstanding(
                        new BigDecimal("100.00"), new BigDecimal("40.00")))
                .isEqualByComparingTo("60.00");
    }
}
