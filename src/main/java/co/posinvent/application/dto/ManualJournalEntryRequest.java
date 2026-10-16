package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for manually posted journal entries ({@code POST /api/v1/journal-entries}).
 *
 * <p>A manual entry is an explicit, auditable human action: it is restricted to
 * {@code ADMIN}/{@code CONTADOR} and MUST carry a justification. The author is recorded
 * through {@code created_by} and the entry is flagged with {@code sourceType = MANUAL}.
 */
public record ManualJournalEntryRequest(
    @NotNull LocalDate entryDate,
    @Size(max = 255) String description,
    @NotBlank @Size(max = 500) String justification,
    @NotEmpty @Valid List<Line> lines
) {
    /**
     * A single posting line. {@code debit}/{@code credit} may be omitted and default to zero;
     * at least one of them must be non-zero on the entry as a whole.
     */
    public record Line(
        @NotNull UUID accountId,
        BigDecimal debit,
        BigDecimal credit,
        @Size(max = 255) String description
    ) {}
}
