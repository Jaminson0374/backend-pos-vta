package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class CashReceiptEvent {
    private final UUID receiptId;
    private final String receiptNumber;
    private final BigDecimal amount;
    private final LocalDate entryDate;
    private final Object source;

    public CashReceiptEvent(Object source, UUID receiptId, String receiptNumber, BigDecimal amount, LocalDate entryDate) {
        this.source = source; this.receiptId = receiptId; this.receiptNumber = receiptNumber;
        this.amount = amount; this.entryDate = entryDate;
    }
    public Object getSource() { return source; }
    public UUID receiptId() { return receiptId; }
    public String receiptNumber() { return receiptNumber; }
    public BigDecimal amount() { return amount; }
    public LocalDate entryDate() { return entryDate; }
}
