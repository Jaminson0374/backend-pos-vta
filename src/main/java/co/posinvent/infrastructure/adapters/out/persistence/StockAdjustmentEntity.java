package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustments")
@Getter
@Setter
public class StockAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "adjustment_type", nullable = false, length = 30)
    private String adjustmentType;

    @Column(name = "quantity_before", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantityAfter;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 6)
    private BigDecimal unitCost;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
