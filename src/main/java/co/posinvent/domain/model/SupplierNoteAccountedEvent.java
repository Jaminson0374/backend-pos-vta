package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published after a supplier debit/credit note is committed (C5) so the accounting listener
 * can post the adjustment against the payable and the bridge account, dated with the note.
 *
 * <ul>
 *   <li>Credit note (the supplier credits us): debit proveedores 2205 / credit puente 220510.</li>
 *   <li>Debit note (the supplier charges us more): debit puente 220510 / credit proveedores 2205.</li>
 * </ul>
 */
public class SupplierNoteAccountedEvent {
    private final UUID noteId;
    private final String noteNumber;
    private final BigDecimal amount;
    private final boolean debitNote;
    private final LocalDate entryDate;
    private final Object source;

    public SupplierNoteAccountedEvent(Object source, UUID noteId, String noteNumber,
                                      BigDecimal amount, boolean debitNote, LocalDate entryDate) {
        this.source = source;
        this.noteId = noteId;
        this.noteNumber = noteNumber;
        this.amount = amount;
        this.debitNote = debitNote;
        this.entryDate = entryDate;
    }

    public Object getSource() { return source; }
    public UUID noteId() { return noteId; }
    public String noteNumber() { return noteNumber; }
    public BigDecimal amount() { return amount; }
    public boolean isDebitNote() { return debitNote; }
    public LocalDate entryDate() { return entryDate; }
}
