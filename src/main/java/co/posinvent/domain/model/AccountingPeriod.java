package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A monthly accounting period identified by its {@code YYYY-MM} key.
 *
 * <p>The engine blocks posting a journal entry whose date falls inside an
 * explicitly {@code CLOSED} period. A period with no stored row is treated as
 * open by default so historical flows keep working.</p>
 */
public record AccountingPeriod(
    String period,
    Status status,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    UUID closedBy
) {
    public enum Status { OPEN, CLOSED }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }
}
