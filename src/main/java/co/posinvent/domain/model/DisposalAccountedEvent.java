package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a disposal/waste operation is committed so the accounting listener can write off
 * the disposed inventory at its Kardex/stock cost (debit pérdida {@code 5195} / credit inventario
 * {@code 1435}) dated with the disposal itself (WU-36c).
 *
 * <p>{@code totalCost} is the cost of the disposed goods ({@code quantity × unitCost}), never the
 * sale price.</p>
 */
public class DisposalAccountedEvent {
    private final UUID disposalId;
    private final String disposalNumber;
    private final BigDecimal totalCost;
    private final LocalDate entryDate;
    private final Object source;

    public DisposalAccountedEvent(Object source, UUID disposalId, String disposalNumber,
                                  BigDecimal totalCost, LocalDate entryDate) {
        this.source = source;
        this.disposalId = disposalId;
        this.disposalNumber = disposalNumber;
        this.totalCost = totalCost;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID disposalId() { return disposalId; }
    public String disposalNumber() { return disposalNumber; }
    public BigDecimal totalCost() { return totalCost; }
    public LocalDate entryDate() { return entryDate; }
}
