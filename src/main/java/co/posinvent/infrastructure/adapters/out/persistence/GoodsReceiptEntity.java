package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.GoodsReceiptStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "goods_receipts")
@Getter
@Setter
public class GoodsReceiptEntity {

    @Id
    private UUID id;

    @Column(name = "oc_id", nullable = false)
    private UUID ocId;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private GoodsReceiptStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReceiptLineItemEntity> lines = new ArrayList<>();

    @Transient
    private List<UUID> batchIds = new ArrayList<>();

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
