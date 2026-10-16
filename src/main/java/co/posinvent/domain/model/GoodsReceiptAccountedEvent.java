package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a goods receipt is committed so the accounting listener can post the
 * inventory-at-physical-control entry (debit inventario 1435 / credit cuenta puente 220510)
 * dated with the receipt itself.
 *
 * <p>The inventory is recognised here, exactly once: the supplier invoice later clears the
 * bridge account instead of debiting inventory again.</p>
 */
public class GoodsReceiptAccountedEvent {
    private final UUID voucherId;
    private final String voucherNumber;
    private final BigDecimal totalCost;
    private final LocalDate entryDate;
    private final Object source;

    public GoodsReceiptAccountedEvent(Object source, UUID voucherId, String voucherNumber,
                                      BigDecimal totalCost, LocalDate entryDate) {
        this.source = source;
        this.voucherId = voucherId;
        this.voucherNumber = voucherNumber;
        this.totalCost = totalCost;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID voucherId() { return voucherId; }
    public String voucherNumber() { return voucherNumber; }
    public BigDecimal totalCost() { return totalCost; }
    public LocalDate entryDate() { return entryDate; }
}
