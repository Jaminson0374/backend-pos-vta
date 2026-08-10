package co.posinvent.infrastructure.adapters.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "production_batches")
@Getter
@Setter
public class ProductionBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "formula_id", nullable = false)
    private UUID formulaId;

    @Column(name = "quantity_produced", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantityProduced;

    @Column(name = "expected_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal expectedQuantity;

    @Column(name = "direct_material_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal directMaterialCost;

    @Column(name = "direct_labor_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal directLaborCost;

    @Column(name = "overhead_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal overheadCost;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "shrinkage_quantity", nullable = false, precision = 15, scale = 4)
    private BigDecimal shrinkageQuantity;

    @Column(name = "shrinkage_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal shrinkageCost;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "batch_id")
    private UUID batchId;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ProductionBatchItemEntity> batchItems = new ArrayList<>();

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
