package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_returns")
@Getter
@Setter
public class PurchaseReturnEntity {

    @Id
    private UUID id;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private UUID receiptId;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "document_number", length = 30)
    private String documentNumber;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseReturnLineEntity> lines = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
