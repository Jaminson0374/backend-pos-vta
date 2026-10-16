package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after an inventory adjustment is committed so the accounting listener can post the
 * inventory/loss entry (inventario {@code 1435} vs pérdida {@code 5195}, by the sign of the delta)
 * dated with the adjustment itself (WU-36c).
 */
public class AdjustmentAppliedEvent {
    private final UUID adjustmentId;
    private final BigDecimal delta;
    private final String reason;
    private final LocalDate entryDate;
    private final Object source;

    public AdjustmentAppliedEvent(Object source, UUID adjustmentId, BigDecimal delta, String reason,
                                  LocalDate entryDate) {
        this.source = source;
        this.adjustmentId = adjustmentId;
        this.delta = delta;
        this.reason = reason;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID adjustmentId() { return adjustmentId; }
    public BigDecimal delta() { return delta; }
    public String reason() { return reason; }
    public LocalDate entryDate() { return entryDate; }
}
