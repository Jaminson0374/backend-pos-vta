package co.posinvent.application.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * One counted denomination in an arqueo request.
 *
 * @param denominationValue face value of the denomination (e.g. 50000)
 * @param denominationType  kind of denomination (BILL or COIN)
 * @param quantity          how many units of this denomination were counted
 */
public record CashCountLineRequest(
        @NotNull BigDecimal denominationValue,
        @NotNull String denominationType,
        int quantity
) {
}
