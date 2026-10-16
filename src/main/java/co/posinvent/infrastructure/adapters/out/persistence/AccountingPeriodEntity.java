package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.AccountingPeriod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounting_periods")
@Getter
@Setter
class AccountingPeriodEntity {

    @Id
    @Column(name = "period", length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private AccountingPeriod.Status status;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @PrePersist
    void prePersist() {
        if (openedAt == null) openedAt = OffsetDateTime.now();
        if (status == null) status = AccountingPeriod.Status.OPEN;
    }
}
