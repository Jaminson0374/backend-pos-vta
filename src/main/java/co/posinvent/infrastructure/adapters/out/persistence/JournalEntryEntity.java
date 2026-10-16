package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
@Getter @Setter
public class JournalEntryEntity {
    @Id private UUID id;
    @Column(name = "entry_number", nullable = false, unique = true) private String entryNumber;
    @Column(name = "entry_date", nullable = false) private LocalDate entryDate;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(name = "source_type", nullable = false) private String sourceType;
    @Column(name = "source_id") private UUID sourceId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "created_by") private UUID createdBy;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<JournalEntryLineEntity> lines = new ArrayList<>();

    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); if (createdAt == null) createdAt = OffsetDateTime.now(); }
}
