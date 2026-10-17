package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after an unapplied supplier advance is committed so the accounting listener can post
 * the asset/cash entry (debit anticipos a proveedores / credit caja) dated with the advance.
 */
public class SupplierAdvanceCreatedEvent {
    private final Object source;
    private final UUID paymentId;
    private final String paymentNumber;
    private final BigDecimal amount;
    private final LocalDate entryDate;

    public SupplierAdvanceCreatedEvent(Object source, UUID paymentId, String paymentNumber,
                                       BigDecimal amount, LocalDate entryDate) {
        this.source = source; this.paymentId = paymentId; this.paymentNumber = paymentNumber;
        this.amount = amount; this.entryDate = entryDate;
    }
    public Object getSource() { return source; }
    public UUID paymentId() { return paymentId; }
    public String paymentNumber() { return paymentNumber; }
    public BigDecimal amount() { return amount; }
    public LocalDate entryDate() { return entryDate; }
}
