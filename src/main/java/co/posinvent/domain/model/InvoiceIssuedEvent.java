package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class InvoiceIssuedEvent {

    private final UUID salesDocumentId;
    private final String invoiceNumber;
    private final BigDecimal subtotal;
    private final BigDecimal taxAmount0;
    private final BigDecimal taxAmount5;
    private final BigDecimal taxAmount8;
    private final BigDecimal taxAmount19;
    private final BigDecimal total;
    private final Object source;

    /**
     * Full constructor with per-rate tax amounts.
     */
    public InvoiceIssuedEvent(Object source, UUID salesDocumentId, String invoiceNumber,
                               BigDecimal subtotal,
                               BigDecimal taxAmount0, BigDecimal taxAmount5,
                               BigDecimal taxAmount8, BigDecimal taxAmount19,
                               BigDecimal total) {
        this.source = source;
        this.salesDocumentId = salesDocumentId;
        this.invoiceNumber = invoiceNumber;
        this.subtotal = subtotal;
        this.taxAmount0 = taxAmount0;
        this.taxAmount5 = taxAmount5;
        this.taxAmount8 = taxAmount8;
        this.taxAmount19 = taxAmount19;
        this.total = total;
    }

    /**
     * Backward-compatible constructor for callers that don't provide per-rate amounts.
     * The full tax amount is stored in {@code taxAmount0} so that {@link #taxAmount()}
     * returns the correct total. Per-rate fields (5, 8, 19) default to {@code BigDecimal.ZERO}.
     */
    public InvoiceIssuedEvent(Object source, UUID salesDocumentId, String invoiceNumber,
                               BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
        this(source, salesDocumentId, invoiceNumber, subtotal,
                taxAmount != null ? taxAmount : BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, total);
    }

    public Object getSource() { return source; }
    public UUID salesDocumentId() { return salesDocumentId; }
    public String invoiceNumber() { return invoiceNumber; }
    public BigDecimal subtotal() { return subtotal; }

    /**
     * Convenience getter — sum of all per-rate tax amounts.
     * Preserved for backward compatibility and fallback scenarios.
     */
    public BigDecimal taxAmount() {
        return taxAmount0.add(taxAmount5).add(taxAmount8).add(taxAmount19);
    }

    public BigDecimal taxAmount0() { return taxAmount0; }
    public BigDecimal taxAmount5() { return taxAmount5; }
    public BigDecimal taxAmount8() { return taxAmount8; }
    public BigDecimal taxAmount19() { return taxAmount19; }
    public BigDecimal total() { return total; }
}
