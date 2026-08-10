package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public class PurchaseAccountedEvent {
    private final UUID invoiceId;
    private final String invoiceNumber;
    private final BigDecimal subtotal;
    private final BigDecimal ivaTotal;
    private final BigDecimal netPayable;
    private final BigDecimal retefuente;
    private final BigDecimal ica;
    private final Object source;

    public PurchaseAccountedEvent(Object source, UUID invoiceId, String invoiceNumber,
                                    BigDecimal subtotal, BigDecimal ivaTotal, BigDecimal netPayable,
                                    BigDecimal retefuente, BigDecimal ica) {
        this.source = source; this.invoiceId = invoiceId; this.invoiceNumber = invoiceNumber;
        this.subtotal = subtotal; this.ivaTotal = ivaTotal; this.netPayable = netPayable;
        this.retefuente = retefuente; this.ica = ica;
    }
    public Object getSource() { return source; }
    public UUID invoiceId() { return invoiceId; }
    public String invoiceNumber() { return invoiceNumber; }
    public BigDecimal subtotal() { return subtotal; }
    public BigDecimal ivaTotal() { return ivaTotal; }
    public BigDecimal netPayable() { return netPayable; }
    public BigDecimal retefuente() { return retefuente; }
    public BigDecimal ica() { return ica; }
}
