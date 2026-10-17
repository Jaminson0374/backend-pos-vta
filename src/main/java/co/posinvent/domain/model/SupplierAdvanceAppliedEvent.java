package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after an advance is applied to a supplier invoice so the accounting listener can
 * cross the asset against the payable (debit proveedores / credit anticipos a proveedores).
 */
public class SupplierAdvanceAppliedEvent {
    private final Object source;
    private final UUID applicationId;
    private final UUID advancePaymentId;
    private final UUID invoiceId;
    private final BigDecimal amount;
    private final LocalDate entryDate;

    public SupplierAdvanceAppliedEvent(Object source, UUID applicationId, UUID advancePaymentId,
                                       UUID invoiceId, BigDecimal amount, LocalDate entryDate) {
        this.source = source; this.applicationId = applicationId;
        this.advancePaymentId = advancePaymentId; this.invoiceId = invoiceId;
        this.amount = amount; this.entryDate = entryDate;
    }
    public Object getSource() { return source; }
    public UUID applicationId() { return applicationId; }
    public UUID advancePaymentId() { return advancePaymentId; }
    public UUID invoiceId() { return invoiceId; }
    public BigDecimal amount() { return amount; }
    public LocalDate entryDate() { return entryDate; }
}
