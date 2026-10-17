package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Supplier net position read model (S3a).
 *
 * <p>Sign convention:
 * {@code netPosition = payableOutstanding - advanceBalance}.</p>
 *
 * <p>A positive {@code netPosition} means the company owes the supplier. A negative value means the
 * supplier has credit in our favor: the unapplied advances (an asset, PUC
 * {@code 133005 — Anticipos a proveedores}) exceed the outstanding accounts payable. This read
 * model is derived from the CxP and the payment advances; it deliberately does not use
 * {@code third_parties.current_balance}, which is AR-only for customers.</p>
 *
 * @param supplierId         the supplier whose position is reported
 * @param payableOutstanding the outstanding accounts payable ({@code SUM(outstanding)}), never null
 * @param advanceBalance     the sum of unapplied advance balances, never null
 * @param netPosition        {@code payableOutstanding - advanceBalance}; negative means credit
 */
public record SupplierBalanceResponse(
        UUID supplierId,
        BigDecimal payableOutstanding,
        BigDecimal advanceBalance,
        BigDecimal netPosition
) {
}
