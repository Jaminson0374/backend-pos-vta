package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a supplier payment is committed so the accounting listener can post the
 * payable/cash entry (debit proveedores / credit caja) dated with the payment itself.
 */
public class SupplierPaymentEvent {
    private final UUID paymentId;
    private final String paymentNumber;
    private final BigDecimal amount;
    private final LocalDate entryDate;
    private final Object source;

    public SupplierPaymentEvent(Object source, UUID paymentId, String paymentNumber,
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
