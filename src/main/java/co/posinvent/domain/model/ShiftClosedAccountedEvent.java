package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a shift is closed (WU-36d) so the accounting listener can post the arqueo
 * adjustment. Only raised when there is a cash-count difference ({@code difference != 0}): a
 * positive difference is a faltante (shortage) and a negative one a sobrante (surplus).
 *
 * <p>The difference follows the Z-Report convention {@code expectedTotal - actualTotal}, so:</p>
 * <ul>
 *   <li>{@code difference > 0} → faltante: debit FALTANTE DE CAJA / credit caja {@code 1105},</li>
 *   <li>{@code difference < 0} → sobrante: debit caja {@code 1105} / credit SOBRANTE DE CAJA.</li>
 * </ul>
 */
public class ShiftClosedAccountedEvent {
    private final UUID shiftId;
    private final String shiftNumber;
    private final BigDecimal difference;
    private final LocalDate entryDate;
    private final Object source;

    public ShiftClosedAccountedEvent(Object source, UUID shiftId, String shiftNumber,
                                     BigDecimal difference, LocalDate entryDate) {
        this.source = source;
        this.shiftId = shiftId;
        this.shiftNumber = shiftNumber;
        this.difference = difference;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID shiftId() { return shiftId; }
    public String shiftNumber() { return shiftNumber; }
    public BigDecimal difference() { return difference; }
    public LocalDate entryDate() { return entryDate; }
}
