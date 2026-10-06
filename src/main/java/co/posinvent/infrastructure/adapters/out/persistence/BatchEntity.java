package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Batch.BatchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class BatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "initial_weight", nullable = false, precision = 10, scale = 3)
    private BigDecimal initialWeight;

    @Column(name = "purchase_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal purchaseCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "created_by", nullable = false)
    @CreatedBy
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @LastModifiedDate
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    @LastModifiedBy
    private UUID updatedBy;

    @Column(name = "source_receipt_id")
    private UUID sourceReceiptId;

    @Column(name = "oc_id")
    private UUID ocId;

    @Column(name = "parent_batch_id")
    private UUID parentBatchId;

    @Column(name = "batch_type", length = 20)
    private String batchType;

    @Column(name = "unit_of_measure_id")
    private UUID unitOfMeasureId;

    // --- Enriched display fields (populated via LEFT JOIN projection, not persisted) ---

    @Transient
    private String productName;

    @Transient
    private String supplierName;

    @Transient
    private String warehouseName;
}
