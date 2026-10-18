package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Customer receivable balance read model (A1).
 *
 * <p>{@code outstanding} is derived from {@code SUM(accounts_receivable.outstanding)} for the
 * client. {@code availableCredit = creditLimit - outstanding}: it can be negative when the client
 * owes more than the credit limit (over-limit is visible, not clamped).</p>
 *
 * @param clientId        the client whose position is reported
 * @param outstanding     the outstanding accounts receivable ({@code SUM(outstanding)}), never null
 * @param creditLimit     the client's credit limit, never null
 * @param availableCredit {@code creditLimit - outstanding}; may be negative
 */
public record CustomerBalanceResponse(
        UUID clientId,
        BigDecimal outstanding,
        BigDecimal creditLimit,
        BigDecimal availableCredit
) {
}
