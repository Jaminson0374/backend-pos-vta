package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a formula production is committed so the accounting listener can post the
 * production entry (WU-36c): the finished goods enter inventory at {@code outputCost} (debit
 * inventario {@code 1435}) while the consumed components leave it at {@code inputCost} (credit
 * inventario {@code 1435}). Any difference between the two — the conversion cost added by the
 * production — is posted to the production variance/cost account.
 */
public class ProductionAccountedEvent {
    private final UUID productionId;
    private final String productionNumber;
    private final BigDecimal inputCost;
    private final BigDecimal outputCost;
    private final LocalDate entryDate;
    private final Object source;

    public ProductionAccountedEvent(Object source, UUID productionId, String productionNumber,
                                    BigDecimal inputCost, BigDecimal outputCost, LocalDate entryDate) {
        this.source = source;
        this.productionId = productionId;
        this.productionNumber = productionNumber;
        this.inputCost = inputCost;
        this.outputCost = outputCost;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID productionId() { return productionId; }
    public String productionNumber() { return productionNumber; }
    public BigDecimal inputCost() { return inputCost; }
    public BigDecimal outputCost() { return outputCost; }
    public LocalDate entryDate() { return entryDate; }
}
