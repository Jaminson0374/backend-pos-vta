package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JournalEntry(
    UUID id,
    String entryNumber,
    LocalDate entryDate,
    String description,
    String sourceType,
    UUID sourceId,
    OffsetDateTime createdAt,
    UUID createdBy,
    String justification,
    List<JournalEntryLine> lines
) {
    public BigDecimal totalDebit() {
        return lines.stream().map(JournalEntryLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalCredit() {
        return lines.stream().map(JournalEntryLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void validate() {
        if (totalDebit().compareTo(BigDecimal.ZERO) == 0 && totalCredit().compareTo(BigDecimal.ZERO) == 0)
            throw new IllegalArgumentException("El asiento debe tener al menos una línea con débito o crédito");
        if (totalDebit().compareTo(totalCredit()) != 0)
            throw new IllegalArgumentException("Débitos no igualan a créditos: débito=" + totalDebit() + " crédito=" + totalCredit());
    }
}
