package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "accounting_template_entries",
       uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "event_type", "account_id"}))
@Getter
@Setter
class AccountingTemplateEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private AccountingTemplateEntity template;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "is_debit", nullable = false)
    private boolean isDebit;

    @Column(nullable = false)
    private int priority;
}
